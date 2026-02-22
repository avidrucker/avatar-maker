(ns avatar.storage
  (:require [avatar.config :as cfg]
            [avatar.db :as db]
            [avatar.render :as render]
            [cljs.reader :as reader]
            [clojure.string :as str]))

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
  (hiccup->svg-str (render/avatar->hiccup @db/!spec)))

(defn edn-export []
  (-> (pr-str @db/!spec)
      (str/replace "," "")))

(defn valid-spec? [x]
  (and (map? x)
       (map? (:parts x))
       (map? (get-in x [:parts :head]))
       (map? (get-in x [:parts :eyes]))
       (map? (get-in x [:parts :hair]))))

(defn load-edn-into-spec! []
  (try
    (let [x (reader/read-string @db/!edn-import-text)]
      (if (valid-spec? x)
        (do
          (reset! db/!edn-import-error nil)
          (reset! db/!spec (render/normalize-spec x))
          (reset! db/!edn-import-text "")
          true)
        (do
          (reset! db/!edn-import-error
                  "EDN parsed, but it doesn't look like a valid avatar spec (missing :parts/:head/:eyes/:hair).")
          false)))
    (catch :default e
      (reset! db/!edn-import-error
              (str "Could not read EDN: " (.-message e)))
      false)))

(defn init! []
  ;; Restore persisted values once per init and keep them synced.
  (reset! db/!spec (or (load-spec) cfg/default-spec))
  (reset! db/!show-svg? (load-bool db/show-svg-key false))
  (reset! db/!active-feature (load-active-feature :head))

  (add-watch db/!spec ::persist-spec
             (fn [_ _ _ new-spec]
               (save-spec! new-spec)))

  (add-watch db/!show-svg? ::persist-show-svg
             (fn [_ _ _ new-v]
               (save-bool! db/show-svg-key new-v)))

  (add-watch db/!active-feature ::persist-active-feature
             (fn [_ _ _ new-feature]
               (save-active-feature! new-feature))))
