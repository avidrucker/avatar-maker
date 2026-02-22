(ns avatar.ui
  (:require [avatar.db :as db]
            [avatar.render :as render]
            [avatar.config :as cfg]
            [avatar.icons :as icons]))

;; -------------------------
;; Pagination (v016 version)
;; -------------------------

(defn paginate
  "Return a page slice of items.
   - page is 0-based
   - per-page is items per page
   Returns {:items [...] :page p :pages N :total T}"
  [items page per-page]
  (let [v (vec (or items []))
        total (count v)
        per (max 1 (or per-page 1))
        pages (max 1 (int (js/Math.ceil (/ total per))))
        p (-> (or page 0) (max 0) (min (dec pages)))
        start (* p per)
        end (min total (+ start per))]
    {:items (subvec v start end)
     :page p
     :pages pages
     :total total}))

(defn page-get [k]
  (get @db/!ui-pages k 0))

(defn page-prev! [k]
  (swap! db/!ui-pages update k (fnil (fn [p] (max 0 (dec p))) 0)))

(defn page-next! [k pages]
  (swap! db/!ui-pages update k
         (fnil (fn [p]
                 (let [last (max 0 (dec (or pages 1)))]
                   (min last (inc p))))
               0)))

(defn pager [page-key pages]
  (when (> pages 1)
    (let [p (page-get page-key)
          prev-disabled? (<= p 0)
          next-disabled? (>= p (dec pages))]
      [:div {:style {:display "flex"
                     :align-items "center"
                     :gap "8px"
                     :margin "8px 0"}}

       [:button {:disabled prev-disabled?
                 :title "Previous"
                 :aria-label "Previous"
                 :style {:width 28 :height 24
                         :opacity (if prev-disabled? 0.35 1)}
                 :on-click #(page-prev! page-key)}
        "◀"]

       [:div {:style {:font-size 12}}
        (str (inc p) "/" pages)]

       [:button {:disabled next-disabled?
                 :title "Next"
                 :aria-label "Next"
                 :style {:width 28 :height 24
                         :opacity (if next-disabled? 0.35 1)}
                 :on-click #(page-next! page-key pages)}
        "▶"]])))

;; -------------------------
;; Head controls (existing)
;; -------------------------

(defn resolve-head-renderer [shape]
  (get-in render/head-registry [shape :render]
          (get-in render/head-registry [:average :render])))

(defn head-preview-svg [shape skin-key]
  (let [head-fn (resolve-head-renderer shape)
        hex (get cfg/skin-tones skin-key)]
    [:svg {:viewBox "-50 -50 100 100"
           :width 48
           :height 48}
     [head-fn {:skin hex}]]))

(defn head-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :head :shape]) shape)]
    ^{:key (name shape)}
    [:button
     {:title label
      :style {:display "flex"
              :align-items "center"
              :justify-content "center"
              :width 68
              :height 68
              :padding 6
              :border (if selected? "2px solid #333" "1px solid #ccc")
              :background "#fff"
              :border-radius 10
              :cursor "pointer"}
      :on-click #(swap! db/!spec assoc-in [:parts :head :shape] shape)}
     (head-preview-svg shape (get-in spec [:parts :head :skin]))]))

(defn head-shape-entries []
  ;; -> [[:average {:label "Average"}] ...] in the order declared by render/head-order
  (vec
   (for [k render/head-order]
     [k {:label (get-in render/head-registry [k :label] (name k))}])))

(def feature-button-gap 6)

(defn head-controls [spec]
  [:div
   [:div {:style {:font-size 12 :margin-bottom 6}} "Head Shape"]

   (let [entries (head-shape-entries)
         paged (paginate entries (page-get :shape/head) 9)]
     [:<>
      [pager :shape/head (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (head-shape-button spec k label)))]])])

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
    :head [head-controls spec]

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

