(ns avatar.config)

(def constants
  {:head/cx 256 :head/cy 270 :head/scale 6.0 :head/y-offset 0
   :eyes/spacing {:min 0.2 :max 1.0 :step 0.01}
   ;; ... add other constants here
   })

(def skin-tones
  {:light-cream "#FFE7B5"
   :golden "#FBC24F"
   :tan "#D67B25"
   :peach "#F7B091"
   :deep-brown "#A4471A"
   :dark-espresso "#4D3411"})

(def default-spec
  {:version 15
   :parts {:head {:shape :average :skin :tan}
           :eyes {:spacing 0.54 :size 1.1 :shape :001 :iris :black}
           ;; ... add other defaults
           }})