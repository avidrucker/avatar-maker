(ns avatar.ui
  (:require [avatar.db :as db]
            [avatar.render :as render]
            [avatar.config :as cfg]))

(defn head-shape-button [shape label selected?]
  ^{:key (name shape)}
  [:button
   {:style {:border (if selected? "2px solid blue" "1px solid gray")
            :margin-right "6px"}
    :on-click #(swap! db/!spec assoc-in [:parts :head :shape] shape)}
   label])

(defn head-shape-picker [spec]
  (let [selected (get-in spec [:parts :head :shape])]
    [:div
     (for [shape render/head-order
           :let [label (get-in render/head-registry [shape :label])]]
       (head-shape-button shape label (= selected shape)))]))

(defn main-panel []
  (let [spec @db/!spec]
    [:div
     [:div "--- Preview ---"]
     [render/avatar->hiccup spec]

     [:div "--- Shapes ---"]
     [head-shape-picker spec]

     [:button {:on-click #(reset! db/!spec cfg/default-spec)} "Reset"]]))
