(ns avatar.render
  (:require [avatar.config :as cfg]))

(defn with-keys
  "Given a seq of hiccup nodes, attach stable React keys.
   If a node already has :key in attrs or ^{:key ...} metadata, we leave it alone."
  [nodes]
  (->> nodes
       (map-indexed
        (fn [i node]
          (cond
            (nil? node) nil

            ;; already has a key via metadata
            (-> node meta :key) node

            ;; already has a key via attrs map
            (and (vector? node)
                 (map? (nth node 1 nil))
                 (contains? (nth node 1) :key))
            node

            ;; otherwise attach a key
            :else
            (with-meta node {:key (str "k" i)}))))
       (remove nil?)))

(defn head-root
  "Wrap head geometry in a group centered on the avatar coordinate system.
   Also auto-keys all children so React doesn't warn when multiple siblings exist."
  [& children]
  (into
   [:g {:transform "translate(-50 -50)"}]
   (with-keys children)))

;; -------------------------
;; Individual Head Renderers
;; -------------------------

(defn head-average [{:keys [skin]}]
  (head-root
   [:path {:d "M73 46C71 70.3594 60.7025 80 50 80C39.2975 80 29 70.3594 27 46C27 31.6406 37.2975 20 50 20C62.7025 20 73 31.6406 73 46Z"
           :fill skin}]))

(defn head-blocky [{:keys [skin]}]
  (head-root
   [:path {:d "M73 46C67 68.3594 80.7025 80 50 80C19.2975 80 33 68.3594 27 46C27 31.6406 37.2975 20 50 20C62.7025 20 73 31.6406 73 46Z"
           :fill skin}]))

(defn head-oval [{:keys [skin]}]
  (head-root
   [:path {:d "M73 46C73 68.3594 64.7025 80 50 80C35.2975 80 27 68.3594 27 46C27 31.6406 37.2975 20 50 20C62.7025 20 73 31.6406 73 46Z"
           :fill skin}]))

(defn head-pointy [{:keys [skin]}]
  (head-root
   [:path {:d "M73 46C73 60.3594 56.7025 80 50 80C43.2975 80 27 60.3594 27 46C27 31.6406 37.2975 20 50 20C62.7025 20 73 31.6406 73 46Z"
           :fill skin}]))

(defn head-egg [{:keys [skin]}]
  (head-root
   [:path {:d "M73 46C73 60.3594 62.7025 80 50 80C37.2975 80 27 60.3594 27 46C27 31.6406 37.2975 20 50 20C62.7025 20 73 31.6406 73 46Z"
           :fill skin}]))

(def head-registry
  {:average {:label "Average" :render head-average :order 0}
   :blocky  {:label "Blocky"  :render head-blocky  :order 1}
   :oval    {:label "Oval"    :render head-oval    :order 2}
   :pointy  {:label "Pointy"  :render head-pointy  :order 3}
   :egg     {:label "Egg"     :render head-egg     :order 4}})

(def head-order
  "Controls display order in the UI."
  [:average :blocky :oval :pointy :egg])

(defn avatar->hiccup [spec]
  (let [{:keys [head]} (:parts spec)
        skin-hex (get cfg/skin-tones (:skin head))
        shape    (:shape head)
        render-fn (get-in head-registry [shape :render] head-average)]
    [:svg {:viewBox "0 0 512 512" } ;; :width 200 :height 200
     [:g {:transform (str "translate(" (:head/cx cfg/geometry) " "
                          (:head/cy cfg/geometry) ") "
                          "scale(" (:head/scale cfg/geometry) ")")}
      ;; optional but nice: key the rendered head by shape so React swaps cleanly
      ^{:key (name shape)}
      [render-fn {:skin skin-hex}]]]))
