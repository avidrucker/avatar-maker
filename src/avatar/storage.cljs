(ns avatar.storage
  (:require [avatar.config :as cfg]
            [avatar.db :as db]
            [avatar.state :as state]
            [avatar.render :as render]
            [cljs.reader :as reader]
            [clojure.string :as str]))

;; -------------------------
;; Storage namespace: Handles persistence of avatar spec and UI state to localStorage.
;; 
;; - Initializes with state/reset-spec! and state/swap-ui!.
;; - Persists via a single watch on state/!app (::persist-app) with field-level diffs for:
;;   - spec
;;   - show-svg flag
;;   - active feature
;; - Uses state/spec/state/ui for SVG/EDN/export/import logic.
;; -------------------------

(defn load-spec []
  (try
    (when-let [s (.getItem js/localStorage db/storage-key)]
      (-> (js->clj (js/JSON.parse s) :keywordize-keys true)
          render/normalize-spec))
    (catch :default _
      nil)))

(defn save-spec! [spec]
  (.setItem js/localStorage db/storage-key
            (js/JSON.stringify (clj->js spec))))

(defn load-bool [k default]
  (let [v (.getItem js/localStorage k)]
    (cond
      (= v "true") true
      (= v "false") false
      :else default)))

(defn save-bool! [k v]
  (.setItem js/localStorage k (str (boolean v))))

(defn load-active-feature [default]
  (let [allowed #{:head :hair :brows :eyes :nose :mouth :other}
        raw (.getItem js/localStorage db/active-feature-key)
        kw (when (seq raw) (keyword raw))]
    (if (contains? allowed kw) kw default)))

(defn save-active-feature! [feature]
  (.setItem js/localStorage db/active-feature-key (name feature)))

(defn attrs->str [m]
  (->> m
       (map (fn [[k v]]
              (str " " (name k) "=\"" v "\"")))
       (apply str)))

(declare hiccup->svg-str)

(defn hiccup->svg-str [node]
  (cond
    (and (vector? node) (= (first node) :<>))
    (apply str (map hiccup->svg-str (rest node)))

    (string? node) node
    (number? node) (str node)
    (nil? node) ""

    (vector? node)
    (let [[tag maybe-attrs & children] node
          ;; Reagent component vector, e.g. [some-fn {:x 1}]
          ;; Evaluate it first, then serialize the returned hiccup.
          _component? (fn? tag)]
      (if _component?
        (let [args (if (map? maybe-attrs)
                     (cons maybe-attrs children)
                     (cons maybe-attrs children))]
          (hiccup->svg-str (apply tag (remove nil? args))))
        (let [[attrs children] (if (map? maybe-attrs)
                                 [maybe-attrs children]
                                 [{} (cons maybe-attrs children)])
              inner (apply str
                           (map hiccup->svg-str (remove nil? children)))]
          (str "<" (name tag) (attrs->str attrs) ">"
               inner
               "</" (name tag) ">"))))

    (seq? node)
    (apply str (map hiccup->svg-str node))

    :else
    (str node)))

(defn svg-source []
  (hiccup->svg-str (render/avatar->hiccup (state/spec))))

(defn edn-export []
  (-> (pr-str (state/spec))
      (str/replace "," "")))

(defn valid-spec? [x]
  (and (map? x)
       (map? (:parts x))
       (map? (get-in x [:parts :head]))
       (map? (get-in x [:parts :eyes]))
       (map? (get-in x [:parts :hair]))))

(defn load-edn-into-spec! []
  (try
    (let [x (reader/read-string (get-in (state/ui) [:edn-import-text]))]
      (if (valid-spec? x)
        (do
          (state/swap-ui! assoc :edn-import-error nil :edn-import-text "")
          (state/reset-spec! (render/normalize-spec x))
          true)
        (do
          (state/swap-ui! assoc :edn-import-error
                          "EDN parsed, but it doesn't look like a valid avatar spec (missing :parts/:head/:eyes/:hair).")
          false)))
    (catch :default e
      (state/swap-ui! assoc :edn-import-error
                      (str "Could not read EDN: " (.-message e)))
      false)))

(defn init! []
  ;; Restore persisted values once per init and keep them synced.
  (state/reset-spec! (or (load-spec) cfg/default-spec))
  (state/swap-ui! assoc
                  :show-svg? (load-bool db/show-svg-key false)
                  :show-presets? (load-bool db/show-presets-key false)
                  :active-feature (load-active-feature :head))

  (remove-watch state/!app ::persist-app)
  (add-watch state/!app ::persist-app
             (fn [_ _ old-app new-app]
               (let [old-spec (:spec old-app)
                     new-spec (:spec new-app)
                     old-show-svg? (get-in old-app [:ui :show-svg?])
                     new-show-svg? (get-in new-app [:ui :show-svg?])
                     old-show-presets? (get-in old-app [:ui :show-presets?])
                     new-show-presets? (get-in new-app [:ui :show-presets?])
                     old-feature (get-in old-app [:ui :active-feature])
                     new-feature (get-in new-app [:ui :active-feature])]
                 (when (not= old-spec new-spec)
                   (save-spec! new-spec))
                 (when (not= old-show-svg? new-show-svg?)
                   (save-bool! db/show-svg-key new-show-svg?))
                 (when (not= old-show-presets? new-show-presets?)
                   (save-bool! db/show-presets-key new-show-presets?))
                 (when (not= old-feature new-feature)
                   (save-active-feature! new-feature))))))
