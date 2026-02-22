(ns avatar.ui
  (:require [avatar.db :as db]
            [avatar.render :as render]
            [avatar.storage :as storage]
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
           :height 48
           :preserveAspectRatio "xMidYMid meet"
           :style {:display "block"}}
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
(def swatch-button-gap 6)
(declare color-swatch-button)

(defn head-shape-panel [spec]
  [:div
   ;; [:div {:style {:font-size 12 :margin-bottom 6}} "Head Shape"]

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

(defn head-swatch-panel [spec]
  (let [selected-skin (get-in spec [:parts :head :skin])
        paged (paginate cfg/skin-swatches (page-get :swatch/skin) 12)]
    [:div
     ;; [:div {:style {:font-size 12 :margin "0 0 6px"}} "Skin Tone"]
     [:<>
      [pager :swatch/skin (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected-skin (:key swatch))
            :swatch swatch
            :on-click #(swap! db/!spec assoc-in
                              [:parts :head :skin]
                              (:key swatch))}]))]]]))

(defn color-swatch-button
  [{:keys [selected? swatch on-click]}]
  [:button
   {:title (:label swatch)
    :aria-label (:label swatch)
    :style {:width 32
            :height 32
            :padding 0
            :border-radius 8
            :border (if selected? "2px solid #333" "1px solid #ccc")
            :cursor "pointer"
            :background (:hex swatch)}
    :on-click on-click}])

(defn hair-preview-svg
  "Preview hair style over the currently selected head shape."
  [spec shape]
  (let [head-shape (get-in spec [:parts :head :shape])
        skin-key (get-in spec [:parts :head :skin])
        color-key (get-in spec [:parts :hair :color])
        head-fn (render/resolve-renderer :head head-shape)
        hair-fn (render/resolve-renderer :hair shape)
        skin-hex (get cfg/skin-tones skin-key)
        hair-hex (get cfg/hair-colors color-key)
        layers (hair-fn {:color hair-hex})]
    [:svg {:viewBox "-50 -50 100 100"
           :width 48
           :height 48
           :preserveAspectRatio "xMidYMid meet"
           :style {:display "block"}}
     (:back layers)
     [head-fn {:skin skin-hex}]
     (:front layers)
     (:front2 layers)]))

(defn hair-shape-panel [spec]
  (let [selected-shape (get-in spec [:parts :hair :shape])
        entries (vec (render/sorted-shape-entries :hair))
        paged (paginate entries (page-get :shape/hair) 9)]
    [:div
     ;; [:div {:style {:font-size 12 :margin-bottom 6}} "Hair Style"]
     [:<>
      [pager :shape/hair (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          ^{:key (name k)}
          [:button
           {:title label
            :style {:width 68
                    :height 68
                    :display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :padding 6
                    :border (if (= selected-shape k)
                              "2px solid #333"
                              "1px solid #ccc")
                    :background "#fff"
                    :border-radius 10
                    :cursor "pointer"}
            :on-click #(swap! db/!spec assoc-in [:parts :hair :shape] k)}
           (hair-preview-svg spec k)]))]]]))

(defn hair-swatch-panel [spec]
  (let [selected-color (get-in spec [:parts :hair :color])
        paged (paginate cfg/hair-swatches (page-get :swatch/hair) 8)]
    [:div
     ;; [:div {:style {:font-size 12 :margin "0 0 6px"}} "Hair Color"]
     [:<>
      [pager :swatch/hair (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(4, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected-color (:key swatch))
            :swatch swatch
           :on-click #(swap! db/!spec assoc-in
                              [:parts :hair :color]
                              (:key swatch))}]))]]]))

(defn eye-preview-svg [shape iris]
  (let [eye-fn (render/resolve-renderer :eyes shape)]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48}
     [:g {:transform "translate(50 50)"}
      (eye-fn {:iris (get cfg/iris-colors iris)})]]))

(defn eye-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :eyes :shape]) shape)
        iris (get-in spec [:parts :eyes :iris])]
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
      :on-click #(swap! db/!spec assoc-in [:parts :eyes :shape] shape)}
     (eye-preview-svg shape iris)]))

(defn eye-shape-panel [spec]
  (let [entries (vec (render/sorted-shape-entries :eyes))
        paged (paginate entries (page-get :shape/eyes) 9)]
    [:div
     ;; [:div {:style {:font-size 12 :margin-bottom 6}} "Eye Shape"]
     [:<>
      [pager :shape/eyes (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (eye-shape-button spec k label)))]]]))

(defn eye-swatch-panel [spec]
  (let [selected-iris (get-in spec [:parts :eyes :iris])
        paged (paginate cfg/iris-swatches (page-get :swatch/iris) 6)]
    [:div
     ;; [:div {:style {:font-size 12 :margin "0 0 6px"}} "Iris Color"]
     [:<>
      [pager :swatch/iris (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected-iris (:key swatch))
            :swatch swatch
            :on-click #(swap! db/!spec assoc-in
                              [:parts :eyes :iris]
                              (:key swatch))}]))]]]))

(defn step-of [k]
  (or (get-in cfg/constants [k :step]) 1))

(defn nudge!
  "Increment a numeric spec value by delta."
  [path delta]
  (swap! db/!spec update-in path (fnil #(+ % delta) 0)))

(defn icon-btn
  [{:keys [title on-click selected? icon]}]
  [:button
   {:title title
    :aria-label title
    :style {:display "flex"
            :align-items "center"
            :justify-content "center"
            :width 64
            :height 64
            :border (if selected? "2px solid #333" "1px solid #ccc")
            :background "#fff"
            :cursor "pointer"}
    :on-click on-click}
   [:div {:style {:transform "scale(2)"}}
    icon]])

(defn brow-preview-svg [shape brow-color-key]
  (let [brow-fn (render/resolve-renderer :brows shape)
        hex (get cfg/hair-colors brow-color-key)]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48}
     [:g {:transform "translate(50 50)"}
      (brow-fn {:color hex})]]))

(defn brow-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :brows :shape]) shape)
        brow-color (get-in spec [:parts :brows :color])]
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
      :on-click #(swap! db/!spec assoc-in [:parts :brows :shape] shape)}
     (brow-preview-svg shape brow-color)]))

(defn brows-nudge-controls []
  (let [color "black"
        dy (step-of :brows/y-offset)
        ds (step-of :brows/size)
        dr (step-of :brows/rotation)
        dx (step-of :brows/x-offset)]
    [:div {:style {:display "grid" :gap 0 :margin "8px 0 12px"}}
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Brows up"
                 :icon (icons/btn-move-up color)
                 :on-click #(nudge! [:parts :brows :y-offset] (- dy))}]
      [icon-btn {:title "Brows down"
                 :icon (icons/btn-move-down color)
                 :on-click #(nudge! [:parts :brows :y-offset] dy)}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Brows bigger"
                 :icon (icons/btn-scale-up color)
                 :on-click #(nudge! [:parts :brows :size] ds)}]
      [icon-btn {:title "Brows smaller"
                 :icon (icons/btn-scale-down color)
                 :on-click #(nudge! [:parts :brows :size] (- ds))}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Rotate clockwise"
                 :icon (icons/btn-rotate-clockwise color)
                 :on-click #(nudge! [:parts :brows :rotation] dr)}]
      [icon-btn {:title "Rotate counter-clockwise"
                 :icon (icons/btn-rotate-counter color)
                 :on-click #(nudge! [:parts :brows :rotation] (- dr))}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Move brows together"
                 :icon (icons/btn-move-together color)
                 :on-click #(nudge! [:parts :brows :x-offset] (- dx))}]
      [icon-btn {:title "Move brows apart"
                 :icon (icons/btn-move-apart color)
                 :on-click #(nudge! [:parts :brows :x-offset] dx)}]]]))

(defn eyes-nudge-controls []
  (let [color "black"
        dy (step-of :eyes/y-offset)
        ds (step-of :eyes/size)
        dr (step-of :eyes/rotation)
        dx (step-of :eyes/spacing)]
    [:div {:style {:display "grid" :gap 0 :margin "8px 0 12px"}}
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Eyes up"
                 :icon (icons/btn-move-up color)
                 :on-click #(nudge! [:parts :eyes :y-offset] (- dy))}]
      [icon-btn {:title "Eyes down"
                 :icon (icons/btn-move-down color)
                 :on-click #(nudge! [:parts :eyes :y-offset] dy)}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Eyes bigger"
                 :icon (icons/btn-scale-up color)
                 :on-click #(nudge! [:parts :eyes :size] ds)}]
      [icon-btn {:title "Eyes smaller"
                 :icon (icons/btn-scale-down color)
                 :on-click #(nudge! [:parts :eyes :size] (- ds))}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Rotate clockwise"
                 :icon (icons/btn-rotate-clockwise color)
                 :on-click #(nudge! [:parts :eyes :rotation] dr)}]
      [icon-btn {:title "Rotate counter-clockwise"
                 :icon (icons/btn-rotate-counter color)
                 :on-click #(nudge! [:parts :eyes :rotation] (- dr))}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Move eyes together"
                 :icon (icons/btn-move-together color)
                 :on-click #(nudge! [:parts :eyes :spacing] (- dx))}]
      [icon-btn {:title "Move eyes apart"
                 :icon (icons/btn-move-apart color)
                 :on-click #(nudge! [:parts :eyes :spacing] dx)}]]]))

(defn nose-preview-svg [shape]
  (let [nose-fn (render/resolve-renderer :nose shape)]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48}
     [:g {:transform "translate(50 50)"}
      (nose-fn {:stroke "black" :fill "none"})]]))

(defn nose-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :nose :shape]) shape)]
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
      :on-click #(swap! db/!spec assoc-in [:parts :nose :shape] shape)}
     (nose-preview-svg shape)]))

(defn nose-shape-panel [spec]
  [:div
   (let [entries (vec (render/sorted-shape-entries :nose))
         paged (paginate entries (page-get :shape/nose) 9)]
     [:<>
      [pager :shape/nose (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (nose-shape-button spec k label)))]] )])

(defn nose-nudge-controls []
  (let [color "black"
        dy (step-of :nose/y-offset)
        ds (step-of :nose/size)]
    [:div {:style {:display "grid" :gap 0 :margin "8px 0 12px"}}
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Nose up"
                 :icon (icons/btn-move-up color)
                 :on-click #(nudge! [:parts :nose :y-offset] (- dy))}]
      [icon-btn {:title "Nose down"
                 :icon (icons/btn-move-down color)
                 :on-click #(nudge! [:parts :nose :y-offset] dy)}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Nose bigger"
                 :icon (icons/btn-scale-up color)
                 :on-click #(nudge! [:parts :nose :size] ds)}]
      [icon-btn {:title "Nose smaller"
                 :icon (icons/btn-scale-down color)
                 :on-click #(nudge! [:parts :nose :size] (- ds))}]]]))

(defn mouth-preview-svg [shape lip-key]
  (let [mouth-fn (render/resolve-renderer :mouth shape)
        lip-hex (get cfg/lip-colors lip-key)]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48}
     [:g {:transform "translate(50 50)"}
      (mouth-fn {:lip-color lip-hex})]]))

(defn mouth-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :mouth :shape]) shape)
        lip (get-in spec [:parts :mouth :color])]
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
      :on-click #(swap! db/!spec assoc-in [:parts :mouth :shape] shape)}
     (mouth-preview-svg shape lip)]))

(defn mouth-shape-panel [spec]
  [:div
   (let [entries (vec (render/sorted-shape-entries :mouth))
         paged (paginate entries (page-get :shape/mouth) 9)]
     [:<>
      [pager :shape/mouth (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (mouth-shape-button spec k label)))]] )])

(defn mouth-swatch-panel [spec]
  (let [selected-color (get-in spec [:parts :mouth :color])
        paged (paginate cfg/lip-swatches (page-get :swatch/lips) 6)]
    [:div
     [:<>
      [pager :swatch/lips (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected-color (:key swatch))
            :swatch swatch
            :on-click #(swap! db/!spec assoc-in
                              [:parts :mouth :color]
                              (:key swatch))}]))]]]))

(defn mouth-nudge-controls []
  (let [color "black"
        dy (step-of :mouth/y-offset)
        ds (step-of :mouth/size)]
    [:div {:style {:display "grid" :gap 0 :margin "8px 0 12px"}}
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Mouth up"
                 :icon (icons/btn-move-up color)
                 :on-click #(nudge! [:parts :mouth :y-offset] (- dy))}]
      [icon-btn {:title "Mouth down"
                 :icon (icons/btn-move-down color)
                 :on-click #(nudge! [:parts :mouth :y-offset] dy)}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Mouth bigger"
                 :icon (icons/btn-scale-up color)
                 :on-click #(nudge! [:parts :mouth :size] ds)}]
      [icon-btn {:title "Mouth smaller"
                 :icon (icons/btn-scale-down color)
                 :on-click #(nudge! [:parts :mouth :size] (- ds))}]]]))

(defn brows-shape-panel [spec]
  [:div
   ;; [:div {:style {:font-size 12 :margin "12px 0 6px"}} "Brow Shape"]
   (let [entries (vec (render/sorted-shape-entries :brows))
         paged (paginate entries (page-get :shape/brows) 9)]
     [:<>
      [pager :shape/brows (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (brow-shape-button spec k label)))]] )])

(defn brows-swatch-panel [spec]
  (let [selected-color (get-in spec [:parts :brows :color])
        paged (paginate cfg/hair-swatches (page-get :swatch/brows) 8)]
    [:div
     ;; [:div {:style {:font-size 12 :margin "0 0 6px"}} "Brow Color"]
     [:<>
      [pager :swatch/brows (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(4, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected-color (:key swatch))
            :swatch swatch
            :on-click #(swap! db/!spec assoc-in
                              [:parts :brows :color]
                              (:key swatch))}]))]]]))

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
      :class "mr2 mb2"
      :style {:padding "6px 10px"
              :border (if active? "2px solid blue" "1px solid gray")
              :background (if active? "#eef5ff" "white")}}
     icon]))

(defn feature-tab-buttons-row []
  [:div {:class "flex flex-wrap"}
   (doall
    (for [tab-btn feature-tab-buttons]
      (feature-tab-btn tab-btn)))])

;; -------------------------
;; Feature Sections
;; -------------------------

(defn active-feature-sections [spec]
  (case @db/!active-feature
    :head  {:shape [head-shape-panel spec]
            :swatches [head-swatch-panel spec]}
    :hair  {:shape [hair-shape-panel spec]
            :swatches [hair-swatch-panel spec]}
    :brows {:shape [brows-shape-panel spec]
            :swatches [brows-swatch-panel spec]
            :nudge [brows-nudge-controls]}
    :eyes  {:shape [eye-shape-panel spec]
            :swatches [eye-swatch-panel spec]
            :nudge [eyes-nudge-controls]}
    :nose  {:shape [nose-shape-panel spec]
            :nudge [nose-nudge-controls]}
    :mouth {:shape [mouth-shape-panel spec]
            :swatches [mouth-swatch-panel spec]
            :nudge [mouth-nudge-controls]}
    :other {:shape [:div "Other controls (coming soon)"]}
    {:shape [:div "Select a feature"]}))

(defn footer-tools-panel []
  (let [show-svg? @db/!show-svg?
        show-edn? @db/!show-edn?
        svg-source (storage/svg-source)
        edn-export (storage/edn-export)]
    [:div
     {:class "ba b--black-20 br3 pa3 mt3"}
     [:div {:class "flex flex-wrap items-center"}
      [:button {:on-click #(reset! db/!spec cfg/default-spec)} "Reset"]
      [:button {:class "ml2"
                :on-click #(reset! db/!show-svg? (not show-svg?))}
       (if show-svg? "Hide SVG source" "Show SVG source")]
      [:button {:class "ml2"
                :on-click #(reset! db/!show-edn? (not show-edn?))}
       (if show-edn? "Hide EDN" "Show EDN")]]

     (when show-svg?
       [:div {:class "mt3 mb3"}
        [:div {:style {:font-size 12 :margin-bottom "4px"}} "SVG Export"]
        [:textarea {:style {:width "100%"
                            :font-family "monospace"
                            :font-size "11px"}
                    :rows 6
                    :read-only true
                    :value svg-source}]])

     (when show-edn?
       [:div {:class "mt3"}
        [:div {:style {:font-size 12 :margin-bottom "4px"}} "EDN Export"]
        [:textarea {:style {:width "100%"
                            :font-family "monospace"
                            :font-size "11px"}
                    :rows 6
                    :read-only true
                    :value edn-export}]

        [:div {:style {:font-size 12 :margin "10px 0 4px"}} "EDN Import"]
        [:textarea {:style {:width "100%"
                            :font-family "monospace"
                            :font-size "11px"}
                    :rows 6
                    :value @db/!edn-import-text
                    :on-input
                    (fn [e]
                      (reset! db/!edn-import-error nil)
                      (reset! db/!edn-import-text (.. e -target -value)))}]

        [:div {:style {:margin-top "8px"}}
         [:button {:on-click #(storage/load-edn-into-spec!)}
          "Load EDN"]]

        (when @db/!edn-import-error
          [:div {:style {:margin-top "8px"
                         :color "#a00"
                         :font-size 12}}
           @db/!edn-import-error])])]))

;; -------------------------
;; Root UI
;; -------------------------

(defn main-panel []
  (let [spec @db/!spec
        {:keys [shape swatches nudge]} (active-feature-sections spec)]
    [:div {:class "pa2"}

     [:div
      {:class "dn db-ns ba b--black-20 br3 pa2 mb3"}
      [feature-tab-buttons-row]]

     [:div
      {:class "flex flex-column flex-row-ns items-start"}

      [:div
       {:class "w-100 w5-ns ba b--black-20 br3 pa3 mb3 mb0-ns mr3-ns"}
       [render/avatar->hiccup spec]
       [:div {:class "db dn-ns mt3"}
        [feature-tab-buttons-row]]]

      [:div
       {:class "w-100 flex-auto ba b--black-20 br3 pa3"}
       [:div
        {:class "controls-layout"}
        [:div {:class "shape-pane"} shape]
        [:div {:class "meta-pane"}
         (when swatches
           [:div {:class "mb3"} swatches])
         (when nudge
           [:div nudge])]]]]

     [footer-tools-panel]]))
