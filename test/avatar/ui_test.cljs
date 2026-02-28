(ns avatar.ui-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [avatar.config :as cfg]
            [avatar.state :as state]
            [avatar.ui :as ui]))

(deftest nudge-helpers-use-config-step-and-clean-rounding
  (testing "nudge delta uses configured step and shared multiplier"
    (is (= 0.02 (ui/nudge-delta :nose/size)))
    (is (= 2 (ui/nudge-delta :nose/y-offset))))
  (testing "quantize-by-step removes floating-point tail noise"
    (is (= 1.13 (ui/quantize-by-step 1.1300000000000001 0.01)))
    (is (= 0.54 (ui/quantize-by-step 0.5400000000000001 0.01)))))

(deftest duplicate-current-preset-detection-ignores-only-id
  (let [base (assoc cfg/default-spec :preset-id "preset-a")
        same-but-new-id (assoc base :preset-id "preset-b")
        changed (assoc-in same-but-new-id [:parts :nose :y-offset] 999)]
    (testing "same preset data with only id changed is considered duplicate"
      (with-redefs [state/spec (fn [] same-but-new-id)
                    ui/all-presets (fn [] [base])]
        (is (true? (ui/duplicate-current-preset?)))))
    (testing "changed preset data is not considered duplicate"
      (with-redefs [state/spec (fn [] changed)
                    ui/all-presets (fn [] [base])]
        (is (false? (ui/duplicate-current-preset?)))))))
