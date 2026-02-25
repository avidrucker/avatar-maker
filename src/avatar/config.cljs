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

(def lip-colors-ordered
  [{:key :natural :label "Natural" :hex "#C97A6B"}
   {:key :rose :label "Rose" :hex "#D46A6A"}
   {:key :deep-rose :label "Deep Rose" :hex "#A33A3A"}
   {:key :plum :label "Plum" :hex "#7A3E52"}
   {:key :brown :label "Brown" :hex "#8A5A4A"}
   {:key :black :label "Black" :hex "black"}])

(def lip-swatches
  "Swatches preserve display order."
  lip-colors-ordered)

(def lip-colors
  (into {} (map (fn [{:keys [key hex]}] [key hex]) lip-colors-ordered)))

;; Glasses/shades colors use the same ordered keys so selection can stay synced
;; when switching styles (regular glasses <-> shades).
(def glasses-color-order
  [:red :blue :gold :silver :brown :charcoal])

(def frame-colors
  {:red "#A32B10"
   :blue "#174580"
   :gold "#A96B08"
   :silver "#ABACA4"
   :brown "#695115"
   :charcoal "#4C5554"})

(def shades-colors
  {:red "#AC5039"
   :blue "#396191"
   :gold "#B87E25"
   :silver "#BFC1BB"
   :brown "#806B34"
   :charcoal "#5C6665"})

(def frame-swatches
  (vec
   (for [k glasses-color-order]
     {:key k :label (name k) :hex (get frame-colors k)})))

(def shades-swatches
  (vec
   (for [k glasses-color-order]
     {:key k :label (name k) :hex (get shades-colors k)})))

(def default-spec
  {:version 16
   :name-id "Mary"
   :parts 
   {:head {:shape :egg :skin :light-cream} 
    :eyes {:spacing 0.54 :size 1 :y-offset 12 :rotation 0 :shape :002 :iris :brown} 
    :hair {:shape :one :color :espresso} 
    :brows {:shape :004 :color :jet-black :size 1 :x-offset -8 :y-offset 12 :rotation 16} 
    :nose {:shape :one :stroke "black" :size 1.04 :y-offset -5} 
    :mouth {:shape :seventeen :color :natural :size 0.94 :y-offset 10} 
    :ears {:shape :none}
    :other {:category :glasses
            :glasses {:shape :none
                      :color :blue
                      :scale 1.0
                      :y-offset 0}}}} 
  )

(def presets
  [default-spec
   {:version 16
    :name-id "Bob"
    :parts
    {:head {:shape :blocky :skin :light-cream}
     :eyes {:spacing 0.54 :size 1 :y-offset 18 :rotation 0 :shape :006 :iris :brown}
     :hair {:shape :two :color :espresso}
     :brows {:shape :001 :color :jet-black :size 1 :x-offset -4 :y-offset 12 :rotation 16}
     :nose {:shape :two :stroke "black" :size 1.04 :y-offset 9}
     :mouth {:shape :four :color :black :size 0.94 :y-offset 20}
     :ears {:shape :none}
     :other {:category :glasses
             :glasses {:shape :glasses_001 :color :silver :scale 1.1 :y-offset 0}}}}
   {:version 16
    :name-id "Kendra"
    :parts
    {:head {:shape :average :skin :deep-brown}
     :eyes {:spacing 0.58 :size 1 :y-offset 12 :rotation 6 :shape :010 :iris :black}
     :hair {:shape :three :color :jet-black}
     :brows {:shape :005 :color :jet-black :size 1.04 :x-offset -8 :y-offset 12 :rotation 16}
     :nose {:shape :nine :stroke "black" :size 1.04 :y-offset 17}
     :mouth {:shape :sixteen :color :black :size 0.94 :y-offset 14}
     :ears {:shape :none}
     :other {:category :glasses
             :glasses {:shape :none :color :blue :scale 1 :y-offset 0}}}}
   
   {:version 16 :name-id "Nameless" :parts {:head {:shape :egg :skin :light-cream} :eyes {:spacing 0.54 :size 1 :y-offset 12 :rotation 0 :shape :001 :iris :gray} :hair {:shape :bald :color :espresso} :brows {:shape :004 :color :chestnut :size 1 :x-offset -8 :y-offset 12 :rotation 16} :nose {:shape :four :stroke "black" :size 0.88 :y-offset -1} :mouth {:shape :three :color :natural :size 0.82 :y-offset 10} :ears {:shape :none} :other {:category :glasses :glasses {:shape :none :color :blue :scale 1 :y-offset 0}}}}
   ])
