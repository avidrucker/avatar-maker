(ns avatar.render-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [avatar.render :as render]))

(deftest normalize-spec-converts-and-defaults
  (testing "string values are keywordized and invalid values fall back to defaults"
    (let [spec {:parts {:head {:shape "average" :skin "tan"}
                        :eyes {:shape "001" :iris "blue"}
                        :hair {:shape "one" :color "espresso"}
                        :brows {:shape "999" :color "not-a-real-color"}
                        :nose {:shape "bogus"}
                        :mouth {:shape "three" :color "rose"}}}
          out (render/normalize-spec spec)]
      (is (= :average (get-in out [:parts :head :shape])))
      (is (= :tan (get-in out [:parts :head :skin])))
      (is (= :001 (get-in out [:parts :eyes :shape])))
      (is (= :blue (get-in out [:parts :eyes :iris])))
      (is (= :none (get-in out [:parts :brows :shape])))
      (is (= :jet-black (get-in out [:parts :brows :color])))
      (is (= :one (get-in out [:parts :nose :shape]))))))

(deftest normalize-spec-handles-glasses-legacy-color-keys
  (testing "legacy lens/frame keys normalize into unified :color"
    (let [spec {:parts {:other {:glasses {:shape "shades_001"
                                          :lens "red"}}}}
          out (render/normalize-spec spec)]
      (is (= :glasses (get-in out [:parts :other :category])))
      (is (= :shades_001 (get-in out [:parts :other :glasses :shape])))
      (is (= :red (get-in out [:parts :other :glasses :color])))
      (is (= 1.0 (get-in out [:parts :other :glasses :scale])))
      (is (= 0 (get-in out [:parts :other :glasses :y-offset]))))))

(deftest clamp-cfg-respects-config-range
  (testing "numeric values are clamped to configured min/max"
    (is (= 2.5 (render/clamp-cfg :nose/size 99)))
    (is (= 0.5 (render/clamp-cfg :nose/size -3)))
    (is (= 1.2 (render/clamp-cfg :nose/size 1.2)))))
