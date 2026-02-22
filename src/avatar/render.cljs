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
;; Eye renderers
;; -------------------------

(defn eye-root
  "Wrap a normalized 100x100 eye SVG so it is centered at (0,0)."
  [& children]
  (into
   [:g {:transform "translate(-50 -50)"}]
   (with-keys children)))

(defn clip-eyeball
  [{:keys [clip-id clip-path]} & children]
  [:<>
   [:defs
    [:clipPath {:id clip-id}
     [:path {:d clip-path}]]]
   [:g {:clip-path (str "url(#" clip-id ")")}
    (into [:<>] (with-keys children))]])

(defn make-eye
  [{:keys [white eyeball outline lids layers]}]
  (fn eye-renderer [{:keys [iris]}]
    (let [clip-id (str "eye-clip-" (random-uuid))
          path (:path white)]
      (eye-root
       (when white
         [:path {:d path :fill "white"}])
       (when eyeball
         (if white
           (clip-eyeball
            {:clip-id clip-id :clip-path path}
            [:circle {:cx (:cx eyeball) :cy (:cy eyeball) :r (:r eyeball)
                      :fill (or iris "#3B5BA5")}]
            [:circle {:cx (:cx eyeball) :cy (:cy eyeball) :r (:pupil-r eyeball)
                      :fill "black"}])
           [:<>
            [:circle {:cx (:cx eyeball) :cy (:cy eyeball) :r (:r eyeball)
                      :fill (or iris "#3B5BA5")}]
            [:circle {:cx (:cx eyeball) :cy (:cy eyeball) :r (:pupil-r eyeball)
                      :fill "black"}]]))
       (when outline
         [:path {:d path
                 :fill "none"
                 :stroke (:stroke outline)
                 :stroke-width (:stroke-width outline)}])
       (for [{:keys [path fill]} layers]
         ^{:key path}
         [:path {:d path :fill (or fill "black")}])
       (for [{:keys [path fill]} lids]
         ^{:key path}
         [:path {:d path :fill (or fill "black")}])))))

(def eye-001
  (make-eye
   {:eyeball {:cx 43.6 :cy 53.6 :r 17.6 :pupil-r 11.2}
    :layers [{:path "M4.27246 50.6373C15.258 35.6327 30.2421 28.9491 44.8389 29.5025C59.358 30.053 73.1435 37.7541 81.8281 50.7808L75.1719 55.2193C67.8565 44.2463 56.3918 37.9472 44.5361 37.4976C32.758 37.051 20.242 42.3676 10.7275 55.3628L4.27246 50.6373Z"
              :fill "black"}]}))

(def eye-002
  (make-eye
   {:eyeball {:cx 41.5 :cy 57.5 :r 22.5 :pupil-r 15}
    :layers [{:path "M41.8291 24.1266C54.4092 23.7851 66.5306 30.7022 74.3799 44.4206L94.9805 38.151L97.0195 44.8483L77.501 50.7878C78.3654 52.8608 79.1472 55.0513 79.834 57.359L76 58.5006L72.166 59.6413C66.2953 39.9158 53.6101 31.8097 42.0459 32.1237C30.3578 32.4411 17.9844 41.3813 12.8496 59.5866L5.15039 57.4147C11.0156 36.62 25.8923 24.5593 41.8291 24.1266Z"
              :fill "black"}]}))

(def eye-003
  (make-eye
   {:eyeball {:cx 38.6 :cy 53.6 :r 17.6 :pupil-r 11.2}
    :layers [{:path "M50 20C66.929 21.1543 75.3109 27.2724 78.4619 37.125L85.6592 34.1377L88.0469 38.6982L79.8232 44.2227C80.0157 46.4449 80.0381 48.7974 79.916 51.2715L91.8135 50.4766L91.7695 55.999L79.1963 58.6426C79.1336 59.0919 79.0693 59.5445 79 60H70.5C67.851 45.39 63.05 38 50 36C25.5 36 22.5 45 12.5 66L5 65C10.5 39 23 21 50 20Z"
              :fill "black"}]}))

(defn eye-004 [{:keys [iris]}]
  (let [clip-id (str "eye004-" (random-uuid))]
    (eye-root
     [:circle {:cx 43 :cy 55 :r 26 :fill "white"}]
     [:defs
      [:clipPath {:id clip-id}
       [:circle {:cx 43 :cy 55 :r 26}]]]
     [:g {:clip-path (str "url(#" clip-id ")")}
      [:circle {:cx 37.6 :cy 46.6 :r 21.6 :fill (or iris "#3C06FF")}]
      [:circle {:cx 37.6 :cy 46.6 :r 13.7455 :fill "black"}]]
     [:path
      {:d "M43 21C61.7777 21 77 36.2223 77 55C77 66.2666 71.5181 76.2508 63.0781 82.4375L58.2773 76.0371C64.7747 71.3105 69 63.6496 69 55C69 40.6406 57.3594 29 43 29C28.6406 29 17 40.6406 17 55H9C9 36.2223 24.2223 21 43 21Z"
       :fill "black"}])))

(defn eye-005 [_]
  (eye-root
   [:path
    {:d "M10.5003 47.5C11.5004 42 25.0004 35.0002 39.0003 36.5C52.8733 37.9864 61.3669 42.466 66.8119 53.4698L85.5003 48L87.0003 53L69.8128 61.1407C70.0494 61.9049 70.279 62.691 70.5003 63.5L64.0003 64.5C55.0003 51.5 44.4389 50.2212 20.0003 55.5C12.3202 56.2201 9.50039 53.0001 10.5003 47.5Z"
     :fill "black"}]))

(defn eye-006 [{:keys [iris]}]
  (let [clip-id (str "eye006-" (random-uuid))
        white-path "M79 58.9997C76 76.9999 21.5 77.9999 13 50.9997C9.7532 41.4998 14.5579 38.7678 20 34.9997C39.1586 32.8073 50.3195 34.5067 71 41.4997C77.6495 45.4972 82 46.9999 79 58.9997Z"]
    (eye-root
     [:path {:d white-path :fill "white"}]
     [:defs
      [:clipPath {:id clip-id}
       [:path {:d white-path}]]]
     [:g {:clip-path (str "url(#" clip-id ")")}
      [:circle {:cx 41.6 :cy 49.6 :r 19.4 :fill (or iris "#3C06FF")}]
      [:circle {:cx 41.6 :cy 49.6 :r 12.3455 :fill "black"}]]
     [:path
      {:d "M44.9592 33.0022C57.6309 33.0236 68.0118 33.9482 75.0519 38.2342C78.8 40.5161 81.5546 43.7062 83.2216 47.9385C84.8488 52.0696 85.3643 56.9969 85.0509 62.7223L77.0635 62.2852C77.3414 57.2089 76.8331 53.5462 75.7791 50.8702C74.765 48.2955 73.1807 46.4602 70.8918 45.0668C65.9425 42.0539 57.6196 40.9999 44.5003 40.9999C44.1934 40.9999 43.8871 40.9646 43.5884 40.8947C37.7965 39.5391 33.1439 38.7381 29.3916 38.5507C25.6268 38.3627 23.0892 38.8125 21.3211 39.6669C18.19 41.1802 15.8476 44.8572 15.0447 54.8248L7.07005 54.1817C7.90474 43.8199 10.5341 35.9953 17.84 32.4644C21.2904 30.7968 25.3378 30.3386 29.7906 30.561C34.1427 30.7783 39.1901 31.6664 44.9592 33.0022Z"
       :fill "black"}])))

(defn eye-007 [{:keys [iris mirrored?]}]
  (eye-root
   [:circle {:cx 43.6 :cy 55.6 :r 30.4 :fill (or iris "#3C06FF")}]
   [:circle {:cx 43.6 :cy 55.6 :r 19.3455 :fill "black"}]
   [:g {:transform
        (when mirrored?
          "translate(43.6 0) scale(-1 1) translate(-43.6 0)")}
    [:path {:d "M52.5 34L56.4775 42.5225L65 46.5L56.4775 50.4775L52.5 59L48.5225 50.4775L40 46.5L48.5225 42.5225L52.5 34Z"
            :fill "white"}]
    [:path {:d "M33.5 56L36.8411 63.1589L44 66.5L36.8411 69.8411L33.5 77L30.1589 69.8411L23 66.5L30.1589 63.1589L33.5 56Z"
            :fill "white"}]]
   [:path {:d "M82 21L72.5059 31.7324C73.3641 32.7785 74.1655 33.8726 74.9082 35.0088L89.4004 29.8457L91.1562 34.8623L78.2305 41.3398C78.9241 43.0395 79.4968 44.8008 79.9375 46.6143L93.999 46.6104L93.9727 51.9248L80.9482 53.5293C80.982 54.1819 81 54.8389 81 55.5C81 55.6669 80.9953 55.8336 80.9932 56H73.9951C73.9968 55.8668 74 55.7332 74 55.5996C73.9998 38.8103 60.389 25.2002 43.5996 25.2002C26.8105 25.2004 13.2004 38.8104 13.2002 55.5996C13.2002 55.7332 13.2034 55.8668 13.2051 56H6.00684C6.00466 55.8336 6 55.6669 6 55.5C6 34.7893 22.7893 18 43.5 18C52.6414 18 61.0175 21.2727 67.5244 26.708L78.5 17L82 21Z"
           :fill "black"}]))

(defn eye-008 [_]
  (eye-root
   [:path {:d "M62.4997 58.4996L64.4997 52.9996C48.4997 48.5 23.9998 47.5 15.4997 50.4996C6.99997 54 12.4996 59.9996 14.9997 59.9996C26.5 60 40 55 62.4997 58.4996Z"
           :fill "black"}]
   [:path {:d "M93.0693 51.001L92.9307 55.999L74.9307 55.499L75.0693 50.501L93.0693 51.001Z"
           :fill "black"}]
   [:path {:d "M87.002 72.71L84.998 77.29L68.998 70.29L71.002 65.71L87.002 72.71Z"
           :fill "black"}]))

(defn eye-009 [_]
  (eye-root
   [:path {:d "M63.5977 29.0049L31.835 48H67V55H32.2725L62.7402 72.4639L59.2588 78.5361L18 55V48L60.0039 22.9971L63.5977 29.0049Z"
           :fill "black"}]))

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

   :eyes
   {:default :001
    :shapes
    {:001 {:label "001" :render eye-001 :order 1}
     :002 {:label "002" :render eye-002 :order 2}
     :003 {:label "003" :render eye-003 :order 3}
   
     :004 {:label "004" :render eye-004 :order 4}
     :005 {:label "005" :render eye-005 :order 5}
     :006 {:label "006" :render eye-006 :order 6}
   
     :007 {:label "007" :render eye-007 :order 7}
     :008 {:label "008" :render eye-008 :order 8}
     :009 {:label "009" :render eye-009 :order 9}}}

   ;; Keep placeholders for non-migrated features so normalization and
   ;; renderer resolution can still be defensive. 

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
      (update-in [:parts :eyes :iris] ->color-kw)
      (update-in [:parts :hair :color] ->color-kw)
      (update-in [:parts :brows :color] ->color-kw)
      
      (update-in [:parts :head :skin]
                 #(if (contains? cfg/skin-tones %)
                    %
                    :light-cream))
      (update-in [:parts :hair :color]
                 #(if (contains? cfg/hair-colors %)
                    %
                    :jet-black))
      (update-in [:parts :brows] 
                 #(or % {:shape :none :color :jet-black :size 1.0 :x-offset 0 :y-offset 0 :rotation 0}))
      (update-in [:parts :brows :color]
                 #(if (contains? cfg/hair-colors %)
                    %
                    :jet-black))
      
      (update-in [:parts :eyes :iris]
                 #(if (contains? cfg/iris-colors %)
                    %
                    :black))
      (update-in [:parts :eyes :spacing] #(or % 0.54))
      (update-in [:parts :eyes :size] #(or % 1.1))
      (update-in [:parts :eyes :y-offset] #(or % 0))
      
      (update-in [:parts :eyes :rotation] #(or % 0))

      ))

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
(defn eyes-svg [{:keys [spacing size y-offset iris shape rotation]}]
  (let [s (clamp-cfg :eyes/spacing (or spacing 0.54))
        sc (clamp-cfg :eyes/size (or size 1.1))
        y (clamp-cfg :eyes/y-offset (or y-offset 0))
        rot (clamp-cfg :eyes/rotation (or rotation 0))
        dx (* (:eyes/dx-scale cfg/geometry) s)
        cy (+ (:eyes/base-y cfg/geometry) y)
        eye-fn (resolve-renderer :eyes shape)
        head-cx (:head/cx cfg/geometry)
        iris-hex (get cfg/iris-colors iris (get cfg/iris-colors :black))]
    [:g
     [:g {:transform
          (str "translate(" (- head-cx dx) " " cy ") "
               "rotate(" (* -1 rot) ") "
               "scale(" sc ") "
               "scale(-1 1)")}
      (eye-fn {:iris iris-hex :mirrored? true})]
     [:g {:transform
          (str "translate(" (+ head-cx dx) " " cy ") "
               "rotate(" rot ") "
               "scale(" sc ")")}
      (eye-fn {:iris iris-hex :mirrored? false})]]))
(defn brows-svg
  [{:keys [shape color size x-offset y-offset rotation]}]
  (when (not= shape :none)
    (let [sc (clamp-cfg :brows/size (or size 1.0))
          xoff (clamp-cfg :brows/x-offset (or x-offset 0))
          yoff (clamp-cfg :brows/y-offset (or y-offset 0))
          rot (clamp-cfg :brows/rotation (or rotation 0))
          head-cx (:head/cx cfg/geometry)
          cy (+ (:brows/base-y cfg/geometry) yoff)
          dx (+ (:brows/base-dx cfg/geometry) xoff)
          brow-fn (resolve-renderer :brows shape)
          brow-color (get cfg/hair-colors color (get cfg/hair-colors :jet-black))]
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
     (brows-svg brows)
     front
     front2
     (glasses-svg (get-in spec* [:parts :other :glasses]))]))
