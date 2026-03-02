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

(defn load-json [k default]
  (try
    (if-let [s (.getItem js/localStorage k)]
      (js->clj (js/JSON.parse s) :keywordize-keys true)
      default)
    (catch :default _
      default)))

(defn save-json! [k value]
  (.setItem js/localStorage k (js/JSON.stringify (clj->js value))))

(defn load-active-feature [default]
  (let [allowed #{:head :hair :brows :eyes :nose :mouth :other}
        raw (.getItem js/localStorage db/active-feature-key)
        kw (when (seq raw) (keyword raw))]
    (if (contains? allowed kw) kw default)))

(defn save-active-feature! [feature]
  (.setItem js/localStorage db/active-feature-key (name feature)))

(defn load-other-subcategory [default]
  (let [allowed #{:glasses :birthmark :mustache :beard}
        raw (.getItem js/localStorage db/other-subcategory-key)
        kw (when (seq raw) (keyword raw))]
    (if (contains? allowed kw) kw default)))

(defn save-other-subcategory! [subcat]
  (.setItem js/localStorage db/other-subcategory-key (name subcat)))

(defn load-theme-mode [default]
  (let [allowed #{:system :light :dark}
        raw (.getItem js/localStorage db/theme-mode-key)
        kw (when (seq raw) (keyword raw))]
    (if (contains? allowed kw) kw default)))

(defn save-theme-mode! [theme-mode]
  (.setItem js/localStorage db/theme-mode-key (name theme-mode)))

(defn apply-theme! [theme-mode]
  (.setAttribute (.-documentElement js/document)
                 "data-theme"
                 (name (or theme-mode :system))))

(defn load-mobile-subpanel [default]
  (let [raw (.getItem js/localStorage db/mobile-subpanel-key)
        raw* (some-> raw
                     (str/replace #"^\"|\"$" "")
                     (str/replace #"^:" "")
                     str/lower-case)]
    (case raw*
      "shape" :shape
      "swatch" :swatches
      "swatches" :swatches
      "color" :swatches
      "nudge" :nudge
      "adjust" :nudge
      default)))

(defn save-mobile-subpanel! [subpanel]
  (.setItem js/localStorage db/mobile-subpanel-key (name subpanel)))

(defn load-hidden-preset-ids []
  (let [v (load-json db/hidden-presets-key [])]
    (if (sequential? v) (vec (filter string? v)) [])))

(defn save-hidden-preset-ids! [ids]
  (save-json! db/hidden-presets-key (vec (or ids []))))

(defn load-user-presets []
  (let [v (load-json db/user-presets-key [])]
    (if (sequential? v)
      (->> v
           (filter map?)
           (map render/normalize-spec)
           vec)
      [])))

(defn save-user-presets! [presets]
  (save-json! db/user-presets-key (vec (or presets []))))

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
                  :theme-mode (load-theme-mode :system)
                  :show-svg? (load-bool db/show-svg-key false)
                  :show-presets? (load-bool db/show-presets-key false)
                  :hidden-preset-ids (load-hidden-preset-ids)
                  :user-presets (load-user-presets)
                  :mobile-subpanel (load-mobile-subpanel :shape)
                  :active-feature (load-active-feature :head)
                  :other-subcategory (load-other-subcategory :glasses))
  (apply-theme! (get-in (state/ui) [:theme-mode]))

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
                     new-feature (get-in new-app [:ui :active-feature])
                     old-theme-mode (get-in old-app [:ui :theme-mode])
                     new-theme-mode (get-in new-app [:ui :theme-mode])
                     old-subcat (get-in old-app [:ui :other-subcategory])
                     new-subcat (get-in new-app [:ui :other-subcategory])
                     old-mobile-subpanel (get-in old-app [:ui :mobile-subpanel])
                     new-mobile-subpanel (get-in new-app [:ui :mobile-subpanel])
                     old-hidden-preset-ids (get-in old-app [:ui :hidden-preset-ids])
                     new-hidden-preset-ids (get-in new-app [:ui :hidden-preset-ids])
                     old-user-presets (get-in old-app [:ui :user-presets])
                     new-user-presets (get-in new-app [:ui :user-presets])]
                 (when (not= old-spec new-spec)
                   (save-spec! new-spec))
                 (when (not= old-show-svg? new-show-svg?)
                   (save-bool! db/show-svg-key new-show-svg?))
                 (when (not= old-show-presets? new-show-presets?)
                   (save-bool! db/show-presets-key new-show-presets?))
                 (when (not= old-feature new-feature)
                   (save-active-feature! new-feature))
                 (when (not= old-theme-mode new-theme-mode)
                   (save-theme-mode! new-theme-mode)
                   (apply-theme! new-theme-mode))
                 (when (not= old-subcat new-subcat)
                   (save-other-subcategory! new-subcat))
                 (when (not= old-mobile-subpanel new-mobile-subpanel)
                   (save-mobile-subpanel! new-mobile-subpanel))
                 (when (not= old-hidden-preset-ids new-hidden-preset-ids)
                   (save-hidden-preset-ids! new-hidden-preset-ids))
                 (when (not= old-user-presets new-user-presets)
                   (save-user-presets! new-user-presets))))))
