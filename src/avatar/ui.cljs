(ns avatar.ui
  (:require [avatar.db :as db]
            [avatar.render :as render]
            [avatar.config :as cfg]
            [avatar.icons :as icons]))

;; -------------------------
;; Head controls (existing)
;; -------------------------

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

;; -------------------------
;; Feature category tabs
;; -------------------------

(def feature-tab-buttons
  ;; Simple + friendly for now; we can swap in SVG icons later (like v015).
  [{:value :head  :label "Head"  :icon (icons/icon-head "black")}
   {:value :hair  :label "Hair"  :icon (icons/icon-hair "black")}
   {:value :brows :label "Brows" :icon (icons/icon-brows "black")}
   {:value :eyes  :label "Eyes"  :icon (icons/icon-eyes "black")}
   {:value :nose  :label "Nose"  :icon (icons/icon-nose "black")}
   {:value :mouth :label "Mouth" :icon (icons/icon-mouth "black")}
   {:value :other :label "Other" :icon (icons/icon-other "black")}])

(defn feature-tab-btn [{:keys [value label icon]}]
  (let [active? (= @db/!active-feature value)]
    ^{:key (name value)}
    [:button
     {:title label
      :aria-label label
      :on-click #(reset! db/!active-feature value)
      :style {:margin-right "6px"
              :padding "6px 10px"
              :border (if active? "2px solid blue" "1px solid gray")
              :background (if active? "#eef5ff" "white")}}
     icon]))

(defn feature-tab-buttons-row []
  [:div
   (doall
    (for [tab-btn feature-tab-buttons]
      (feature-tab-btn tab-btn)))])

;; -------------------------
;; Controls panel dispatcher
;; -------------------------

(defn controls-panel [spec]
  (case @db/!active-feature
    :head
    [:div
     [:div "--- Shapes ---"]
     [head-shape-picker spec]]

    ;; stubs for now — we’ll fill these in as v016 grows
    :hair  [:div "Hair controls (coming soon)"]
    :brows [:div "Brow controls (coming soon)"]
    :eyes  [:div "Eye controls (coming soon)"]
    :nose  [:div "Nose controls (coming soon)"]
    :mouth [:div "Mouth controls (coming soon)"]
    :other [:div "Other controls (coming soon)"]

    [:div "Select a feature"]))

;; -------------------------
;; Root UI
;; -------------------------

(defn main-panel []
  (let [spec @db/!spec]
    [:div
     {:style {:display "flex"
              :gap "10px"
              :align-items "flex-start"
              :padding "16px"}}

     [:div {:style {:flex "0 0 260px"}}
      [render/avatar->hiccup spec]]

     [:div
      {:style {:flex "1 1 auto"
               :min-width "320px"
               :padding "12px"
               :border "1px solid #ddd"
               :border-radius "10px"}}

      [:div {:style {:margin-bottom "12px"}}
       [feature-tab-buttons-row]]

      [:div {:style {:margin-bottom "12px"}}
       [controls-panel spec]]

      [:div {:style {:display "flex" :gap "8px"}}
       [:button {:on-click #(reset! db/!spec cfg/default-spec)} "Reset"]]]]))

