(ns avatar.render
  (:require [avatar.config :as cfg]))

(defn with-keys
  "Given a seq of hiccup nodes, attach stable React keys.
   If a node already has :key in attrs or ^{:key ...} metadata, leave it alone."
  [nodes]
  (->> nodes
       (map-indexed
        (fn [i node]
          (cond
            (nil? node) nil
            (-> node meta :key) node
            (and (vector? node)
                 (map? (nth node 1 nil))
                 (contains? (nth node 1) :key))
            node
            :else
            (with-meta node {:key (str "k" i)}))))
       (remove nil?)))

(defn head-root
  "Wrap geometry in a group centered on a normalized 100x100 asset space."
  [& children]
  (into
   [:g {:transform "translate(-50 -50)"}]
   (with-keys children)))

(defn hair-root
  "Center a normalized 100x100 hair asset at (0,0)."
  [& children]
  (into
   [:g {:transform "translate(-50 -50)"}]
   (with-keys children)))

;; -------------------------
;; Head renderers
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

;; -------------------------
;; Hair renderers
;; -------------------------

(defn make-hair
  [{:keys [front front2 back]}]
  (fn hair-renderer [{:keys [color]}]
    {:front
     (when front
       (hair-root
        [:path {:d front :fill color}]))

     :front2
     (when front2
       (hair-root
        [:path {:d front2 :fill color}]))

     :back
     (when back
       (hair-root
        [:path {:d back :fill color}]))}))

(def hair-001
  (make-hair
   {:front
    "M30.5003 44.5L28.0003 56C28.0003 56 21.4767 48 28.0003 31C34.5031 14.0542 65.5072 13.6002 72.0003 31C77.5 49 72.0003 56 72.0003 56L69.0003 44.5L66.5003 48L62.5003 39.5L58.5003 45L53.5003 36.5L49.5003 44.5L45.5003 36.5L41.0003 44.5L37 39.5L33.5003 48L30.5003 44.5Z"}))

(def hair-002
  (make-hair
   {:back
    "M31.0001 76.5L30.0001 79.5C11.0001 60 24.4999 33 31.0001 27 C40.5001 15 59.5 17 69.0001 27 C82.7775 46.8304 83.5 63 69.0001 79.5 L67.5001 76.5L65.5001 79.5 C63.7238 74.9543 62.5313 72.5368 58.5001 69.5 H40.5001 C36.7742 73.0768 35.4795 75.3017 34.0001 79.5 L31.0001 76.5Z"

    :front
    "M58 20.5 C53.5 34.5 42.0001 50.5 21.5002 63 C20.0001 55 20.5 48 24.0001 38 C34.5 17.5 47.5 17 58 20.5Z"

    :front2
    "M52 19 C70 19.5 82.5 42.5 78.5 64 L52 19Z"}))

(def hair-bald
  (make-hair {}))

;; -------------------------
;; Brow renderers
;; -------------------------

(defn brow-root
  "Center a normalized 100x100 brow asset at (0,0)."
  [& children]
  (into
   [:g {:transform "translate(-50 -50)"}]
   (with-keys children)))

(defn make-brow
  "Create a brow renderer from one normalized SVG element."
  [element]
  (fn brow-renderer [{:keys [color]}]
    (brow-root
     (let [[tag attrs] element]
       [tag (assoc attrs :fill (or color "black"))]))))

(defn brow-none [_] nil)

(def brow-001
  (make-brow
   [:path {:d "M62 27.5L6.5 56.5V66.5L80.5 45L62 27.5Z"}]))

(def brow-002
  (make-brow
   [:path {:d "M8.50037 56.5C42.0398 28.635 57.5429 28.4721 81.5004 45.5C82.5 48.5 80.5 50 78.0004 49C59.83 38.4599 47.5507 36.6841 12.5004 59.5C9.50032 60.5 8.00003 59.5 8.50037 56.5Z"}]))

(def brow-003
  (make-brow
   [:path {:d "M55.5 32.5L5.5 48L9 66C57 52 58 51.5 82 57.5L55.5 32.5Z"}]))

(def brow-004
  (make-brow
   [:path {:d "M11.0914 54.0113C43.5529 29.3355 58.5578 29.1912 81.7453 44.2703C82.7128 46.9269 80.777 48.2552 78.3578 47.3697C57.0956 37.2186 48.7578 42.1117 21.5 62.5C13.6126 69.0701 5.13936 62.903 11.0914 54.0113Z"}]))

(def brow-005
  (make-brow
   [:path {:d "M79 43C49 41.6036 39.9558 45.8628 29 64.0001C22 71.5 9.5 68 9.5 55.0001C27 27 55.5 24.9999 79 43Z"}]))

(def brow-006
  (make-brow
   [:rect {:x 11 :y 44 :width 63 :height 17}]))

(def brow-007
  (make-brow
   [:path {:d "M8 43.4999C34.6082 43.1703 46.492 43.3306 55.5 44.9999C64.8392 48.4078 69.6547 51.8843 78 58.9999L73.5 66.9999C65.5721 62.596 61.275 60.3375 55.5 58.9999C37.5 57.4999 26.7077 61.4164 8 59.9999V43.4999Z"}]))

(def brow-008
  (make-brow
   [:path {:d "M44 44.4999C57.5 35 73.5 39.5 83 51.4999C68.388 46.1004 60.2547 43.7399 47 53.4999C26 65.5 11 63 9 57.9999C10.5 47.5 23 57.9999 44 44.4999Z"}]))

;; -------------------------
;; Feature registry
;; -------------------------

(def feature-registry
  {:head
   {:default :average
    :shapes head-registry}

   :hair
   {:default :bald
    :shapes
    {:bald {:label "Bald" :render hair-bald :order 0}
     :one {:label "Hair 001" :render hair-001 :order 1}
     :two {:label "Hair 002" :render hair-002 :order 2}}}

   ;; Keep placeholders for non-migrated features so normalization and
   ;; renderer resolution can still be defensive.
   :eyes {:default :none :shapes {:none {:label "None" :render (fn [_] nil) :order 0}}}
   :nose {:default :none :shapes {:none {:label "None" :render (fn [_] nil) :order 0}}}
   :ears {:default :none :shapes {:none {:label "None" :render (fn [_] nil) :order 0}}}
   :mouth {:default :none :shapes {:none {:label "None" :render (fn [_] nil) :order 0}}}
   :brows
   {:default :none
    :shapes
    {:none {:label "None" :render brow-none :order 0}
     :001 {:label "001" :render brow-001 :order 1}
     :002 {:label "002" :render brow-002 :order 2}
     :003 {:label "003" :render brow-003 :order 3}
     :004 {:label "004" :render brow-004 :order 4}
     :005 {:label "005" :render brow-005 :order 5}
     :006 {:label "006" :render brow-006 :order 6}
     :007 {:label "007" :render brow-007 :order 7}
     :008 {:label "008" :render brow-008 :order 8}}}})

(defn sorted-shape-entries [feature]
  (->> (get-in feature-registry [feature :shapes])
       (sort-by (fn [[_ {:keys [order]}]] (or order 9999)))
       vec))

(defn resolve-renderer [feature shape]
  (or (get-in feature-registry [feature :shapes shape :render])
      (get-in feature-registry [feature :shapes (get-in feature-registry [feature :default]) :render])
      (fn [_] nil)))

;; -------------------------
;; Spec normalization
;; -------------------------

(defn valid-shape?
  [feature shape]
  (contains? (get-in feature-registry [feature :shapes]) shape))

(defn ->kw [x]
  (cond
    (keyword? x) x
    (string? x)  (keyword x)
    :else        nil))

(defn normalize-feature
  "Fix JSON string -> keyword mismatch, enforce valid shape values, apply defaults defensively."
  [spec feature]
  (let [default-shape (get-in feature-registry [feature :default])]
    (update-in spec [:parts feature]
               (fn [part]
                 (let [shape (->kw (:shape part))]
                   (if (valid-shape? feature shape)
                     (assoc (or part {}) :shape shape)
                     (assoc (or part {}) :shape default-shape)))))))

(defn ->color-kw [v]
  (cond
    (keyword? v) v
    (string? v)  (keyword v)
    :else        nil))

(defn normalize-spec [spec]
  (-> spec
      (normalize-feature :eyes)
      (normalize-feature :head)
      (normalize-feature :nose)
      (normalize-feature :ears)
      (normalize-feature :mouth)
      (normalize-feature :hair)
      (normalize-feature :brows)
      (update-in [:parts :head :skin] ->color-kw)
      (update-in [:parts :hair :color] ->color-kw)
      (update-in [:parts :head :skin]
                 #(if (contains? cfg/skin-tones %)
                    %
                    :light-cream))
      (update-in [:parts :hair :color]
                 #(if (contains? cfg/hair-colors %)
                    %
                    :jet-black))
      (update-in [:parts :brows] #(or % {:shape :none :size 1.0 :x-offset 0 :y-offset 0 :rotation 0}))))

(defn clamp
  [mn mx v]
  (-> v (max mn) (min mx)))

(defn clamp-cfg
  [k value]
  (let [{:keys [min max]} (get cfg/constants k)]
    (if (and (number? min) (number? max) (number? value))
      (clamp min max value)
      value)))

;; -------------------------
;; Avatar render pipeline
;; -------------------------

(defn head-transform []
  (str "translate(" (:head/cx cfg/geometry) " "
       (+ (:head/cy cfg/geometry) (:head/y-offset cfg/geometry)) ") "
       "scale(" (:head/scale cfg/geometry) ")"))

(defn head-svg [{:keys [shape skin]}]
  (let [renderer (resolve-renderer :head shape)
        skin-hex (get cfg/skin-tones skin (get cfg/skin-tones :light-cream))]
    [:g {:transform (head-transform)}
     [renderer {:skin skin-hex}]]))

(defn hair-svg [{:keys [shape color]}]
  (let [renderer (resolve-renderer :hair shape)
        layers   (renderer {:color (get cfg/hair-colors color (get cfg/hair-colors :jet-black))})]
    {:back
     (when (:back layers)
       [:g {:transform (head-transform)}
        (:back layers)])

     :front
     (when (:front layers)
       [:g {:transform (head-transform)}
        (:front layers)])

     :front2
     (when (:front2 layers)
       [:g {:transform (head-transform)}
        (:front2 layers)])}))

(defn ears-svg [_ears _head] nil)
(defn mouth-svg [_mouth] nil)
(defn nose-svg [_nose _head] nil)
(defn eyes-svg [_eyes] nil)
(defn brows-svg
  [{:keys [shape size x-offset y-offset rotation]} hair]
  (when (not= shape :none)
    (let [sc (clamp-cfg :brows/size (or size 1.0))
          xoff (clamp-cfg :brows/x-offset (or x-offset 0))
          yoff (clamp-cfg :brows/y-offset (or y-offset 0))
          rot (clamp-cfg :brows/rotation (or rotation 0))
          head-cx (:head/cx cfg/geometry)
          cy (+ (:brows/base-y cfg/geometry) yoff)
          dx (+ (:brows/base-dx cfg/geometry) xoff)
          brow-fn (resolve-renderer :brows shape)
          brow-color (get cfg/hair-colors (:color hair) (get cfg/hair-colors :jet-black))]
      [:g
       [:g {:transform
            (str "translate(" (- head-cx dx) " " cy ") "
                 "rotate(" (* -1 rot) ") "
                 "scale(" sc ") "
                 "scale(-1 1)")}
        (brow-fn {:color brow-color})]
       [:g {:transform
            (str "translate(" (+ head-cx dx) " " cy ") "
                 "rotate(" rot ") "
                 "scale(" sc ")")}
        (brow-fn {:color brow-color})]])))
(defn glasses-svg [_glasses] nil)

(defn avatar->hiccup
  "Layering: back hair -> ears -> head -> mouth -> nose -> eyes -> brows -> front hair -> glasses."
  [spec]
  (let [spec* (normalize-spec spec)
        {:keys [head eyes ears mouth nose hair brows]} (:parts spec*)
        {:keys [back front front2]} (hair-svg hair)]
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :viewBox "0 0 512 512"
           :width 200
           :height 200}
     back
     (ears-svg ears head)
     (head-svg head)
     (mouth-svg mouth)
     (nose-svg nose head)
     (eyes-svg eyes)
     (brows-svg brows hair)
     front
     front2
     (glasses-svg (get-in spec* [:parts :other :glasses]))]))
