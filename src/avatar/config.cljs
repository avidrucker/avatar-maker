(ns avatar.config)

(def constants
  "Defines UI constraints used by controls."
  {:eyes/spacing {:min 0.2 :max 1.0 :step 0.01}
   :eyes/size {:min 0.5 :max 1.8 :step 0.01}
   :eyes/y-offset {:min -80 :max 80 :step 1}
   :eyes/rotation {:min -45 :max 45 :step 1}

   :brows/size {:min 0.5 :max 2.5 :step 0.01}
   :brows/x-offset {:min -80 :max 80 :step 1}
   :brows/y-offset {:min -120 :max 120 :step 1}
   :brows/rotation {:min -45 :max 45 :step 1}

   :nose/size {:min 0.5 :max 2.5 :step 0.01}
   :nose/y-offset {:min -60 :max 60 :step 1}

   :mouth/size {:min 0.5 :max 2.5 :step 0.01}
   :mouth/y-offset {:min -80 :max 80 :step 1}

   :glasses/scale {:min 0.5 :max 2.5 :step 0.01}
   :glasses/y-offset {:min -120 :max 120 :step 1}

   :ears/size {:min 1.0 :max 3.0 :step 0.01}
   :ears/x-offset {:min -40 :max 40 :step 1}
   :ears/y-offset {:min 0 :max 80 :step 1}
   :ears/rotation {:min -45 :max 45 :step 1}})

(def geometry
  {:head/cx 256
   :head/cy 270
   :head/scale 6.0
   :head/y-offset 0

   :nose/base-y 320
   :nose/scale 1.25
   :nose/stroke-width 2

   :eyes/base-y 260
   :eyes/dx-scale 110
   :eyes/rotate-left 0
   :eyes/rotate-right 0

   :brows/base-y 225
   :brows/base-dx 70

   :mouth/base-y 365
   :mouth/scale 1.2

   :glasses/base-y 280
   :glasses/scale 3.0

   :ears/head-rx 160
   :ears/distance-padding 10
   :ears/base-y-offset -30
   :ears/rotate-left -15
   :ears/rotate-right 15
   })

(def skin-tones
  {:light-cream "#FFE7B5"
   :golden "#FBC24F"
   :tan "#D67B25"
   :peach "#F7B091"
   :deep-brown "#A4471A"
   :dark-espresso "#4D3411"})

(def skin-colors-ordered
  [{:key :light-cream :label "Light Cream" :hex "#FFE7B5"}
   {:key :golden :label "Golden" :hex "#FBC24F"}
   {:key :tan :label "Tan" :hex "#D67B25"}
   {:key :peach :label "Peach" :hex "#F7B091"}
   {:key :deep-brown :label "Deep Brown" :hex "#A4471A"}
   {:key :dark-espresso :label "Dark Espresso" :hex "#4D3411"}])

(def skin-swatches
  "Swatches preserve display order."
  skin-colors-ordered)

(def hair-colors-ordered
  "Natural hair colors."
  [;; Top row (dark -> lighter browns)
   {:key :jet-black :label "Jet Black" :hex "#373633"}
   {:key :espresso :label "Espresso" :hex "#594432"}
   {:key :mahogany :label "Mahogany" :hex "#764931"}
   {:key :chestnut :label "Chestnut" :hex "#8E603E"}
   ;; Bottom row (grays -> lighter tones)
   {:key :steel-gray :label "Steel Gray" :hex "#919294"}
   {:key :dark-brown :label "Dark Brown" :hex "#675537"}
   {:key :medium-brown :label "Medium Brown" :hex "#997A41"}
   {:key :sandy-blonde :label "Sandy Blonde" :hex "#CFB87E"}])

(def hair-swatches
  "Swatches preserve display order."
  hair-colors-ordered)

(def hair-colors
  (into {} (map (fn [{:keys [key hex]}] [key hex]) hair-colors-ordered)))

(def iris-colors-ordered
  [{:key :black :label "Black" :hex "black"}
   {:key :blue :label "Blue" :hex "#2C5DC6"}
   {:key :gray :label "Gray" :hex "#7F838C"}
   {:key :olive :label "Olive" :hex "#848540"}
   {:key :brown :label "Brown" :hex "#7A5A4A"}
   {:key :teal :label "Teal" :hex "#4D8873"}])

(def iris-swatches
  "Swatches preserve display order."
  iris-colors-ordered)

(def iris-colors
  (into {} (map (fn [{:keys [key hex]}] [key hex]) iris-colors-ordered)))

(def default-spec
  {:version 16
   :parts {:head {:shape :average :skin :tan}
           :eyes {:spacing 0.54 :size 1.1 :y-offset 0 :rotation 0 :shape :001 :iris :black}
           :hair {:shape :bald :color :jet-black}
           :brows {:shape :none :color :jet-black :size 1.0 :x-offset 0 :y-offset 0 :rotation 0}
           }})
