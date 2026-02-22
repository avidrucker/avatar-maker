(ns avatar.config)

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

(def default-spec
  {:version 15
   :parts {:head {:shape :average :skin :tan}
           :eyes {:spacing 0.54 :size 1.1 :shape :001 :iris :black}
           :hair {:shape :bald :color :jet-black}
           }})
