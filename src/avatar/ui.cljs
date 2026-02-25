(ns avatar.ui
  (:require [avatar.state :as state]
            [avatar.render :as render]
            [avatar.storage :as storage]
            [avatar.config :as cfg]
            [avatar.icons :as icons]
            [reagent.core :as r]))

;; -------------------------
;; UI namespace: UI components and controls for avatar maker app.
;;
;; - Reads spec from state/spec in main-panel.
;; - Uses state/ui for active feature, other subcategory, footer toggles, and EDN import/error reads.
;; - Uses state/swap-spec! for all spec edits.
;; - Uses state/swap-ui! for tab/toggle/input updates.
;; - Pagination now reads/writes [:ui :ui-pages] through state helpers.
;; -------------------------

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
  (get-in (state/ui) [:ui-pages k] 0))

(defn page-prev! [k]
  (state/swap-ui! update :ui-pages
                  (fn [pages]
                    (update (or pages {}) k (fnil (fn [p] (max 0 (dec p))) 0)))))

(defn page-next! [k pages]
  (state/swap-ui! update :ui-pages
                  (fn [page-map]
                    (update (or page-map {}) k
                            (fnil (fn [p]
                                    (let [last (max 0 (dec (or pages 1)))]
                                      (min last (inc p))))
                                  0)))))

(defn pager [page-key pages]
  (when (> pages 1)
    (let [p (page-get page-key)
          prev-disabled? (<= p 0)
          next-disabled? (>= p (dec pages))]
      [:div {:class "pb2"
             :style {:display "flex"
                     :align-items "center"
                     :gap "8px"
                     }}

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
      :on-click #(state/swap-spec! assoc-in [:parts :head :shape] shape)}
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
   ;; head shape panel
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
     ;; skin tone panel / head swatch panel
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
            :on-click #(state/swap-spec! assoc-in
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
     ;; hair style panel
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
            :on-click #(state/swap-spec! assoc-in [:parts :hair :shape] k)}
           (hair-preview-svg spec k)]))]]]))

(defn hair-swatch-panel [spec]
  (let [selected-color (get-in spec [:parts :hair :color])
        paged (paginate cfg/hair-swatches (page-get :swatch/hair) 8)]
    [:div
     ;; hair color panel
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
            :on-click #(state/swap-spec! assoc-in
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
      :on-click #(state/swap-spec! assoc-in [:parts :eyes :shape] shape)}
     (eye-preview-svg shape iris)]))

(defn eye-shape-panel [spec]
  (let [entries (vec (render/sorted-shape-entries :eyes))
        paged (paginate entries (page-get :shape/eyes) 9)]
    [:div
     ;; eye shape panel
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
     ;; iris color panel / eye swatch panel
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
            :on-click #(state/swap-spec! assoc-in
                              [:parts :eyes :iris]
                              (:key swatch))}]))]]]))

(defn step-of [k]
  (or (get-in cfg/constants [k :step]) 1))

(def nudge-factor-default
  "Global multiplier for nudge step size."
  2)

(def nudge-factor-by-key
  "Optional per-control override for nudge multipliers.
   Any key not listed here uses nudge-factor-default."
  {})

(defn nudge-factor [k]
  (get nudge-factor-by-key k nudge-factor-default))

(defn nudge-delta [k]
  (* (step-of k) (nudge-factor k)))

(defn path->cfg-key
  [path]
  (let [[a b c d] path]
    (cond
      ;; [:parts :feature :attr]
      (and (= a :parts) (keyword? b) (keyword? c) (nil? d))
      (keyword (str (name b) "/" (name c)))

      ;; [:parts :other :glasses :attr]
      (and (= a :parts) (= b :other) (keyword? c) (keyword? d))
      (keyword (str (name c) "/" (name d)))

      :else nil)))

(defn decimals-of [n]
  (let [s (str n)
        i (.indexOf s ".")]
    (if (= -1 i) 0 (- (count s) i 1))))

(defn quantize-by-step
  "Snap value to step precision using integer math, then format back."
  [value step]
  (let [d (decimals-of step)
        scale (js/Math.pow 10 d)
        scaled (js/Math.round (* value scale))]
    (js/Number (.toFixed (/ scaled scale) d))))

(defn nudge!
  "Increment a numeric spec value by delta, clamp, then quantize by step."
  [path delta]
  (let [cfg-key (path->cfg-key path)
        step (step-of cfg-key)]
    (state/swap-spec! update-in path
           (fnil (fn [v]
                   (let [next (+ v delta)
                         clamped (render/clamp-cfg cfg-key next)]
                     (quantize-by-step clamped step)))
                 0))))

(defn icon-btn
  [{:keys [title on-click selected? icon]}]
  [:button
   {:title title
    :aria-label title
    :class "w23 h23 w3-ns h3-ns"
    :style {:display "flex"
            :align-items "center"
            :justify-content "center"
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
      :on-click #(state/swap-spec! assoc-in [:parts :brows :shape] shape)}
     (brow-preview-svg shape brow-color)]))

(defn brows-nudge-controls []
  (let [color "black"
        dy (nudge-delta :brows/y-offset)
        ds (nudge-delta :brows/size)
        dr (nudge-delta :brows/rotation)
        dx (nudge-delta :brows/x-offset)]
    [:div {:style {:display "grid" :gap 0}}
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
        dy (nudge-delta :eyes/y-offset)
        ds (nudge-delta :eyes/size)
        dr (nudge-delta :eyes/rotation)
        dx (nudge-delta :eyes/spacing)]
    [:div {:style {:display "grid" :gap 0}}
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
      :on-click #(state/swap-spec! assoc-in [:parts :nose :shape] shape)}
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
        dy (nudge-delta :nose/y-offset)
        ds (nudge-delta :nose/size)]
    [:div {:style {:display "grid" :gap 0}}
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
      :on-click #(state/swap-spec! assoc-in [:parts :mouth :shape] shape)}
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
            :on-click #(state/swap-spec! assoc-in
                              [:parts :mouth :color]
                              (:key swatch))}]))]]]))

(defn mouth-nudge-controls []
  (let [color "black"
        dy (nudge-delta :mouth/y-offset)
        ds (nudge-delta :mouth/size)]
    [:div {:style {:display "grid" :gap 0}}
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
   ;; brow shape panel
   (let [entries (vec (render/sorted-shape-entries :brows))
         paged (paginate entries (page-get :shape/brows) 9)]
     [:<>
      [pager :shape/brows (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (brow-shape-button spec k label)))]])])

(defn brows-swatch-panel [spec]
  (let [selected-color (get-in spec [:parts :brows :color])
        paged (paginate cfg/hair-swatches (page-get :swatch/brows) 8)]
    [:div 
    ;; brow color panel / brow swatch panel
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
           :on-click #(state/swap-spec! assoc-in
                              [:parts :brows :color]
                              (:key swatch))}]))]]]))

(defn glasses-kind [shape]
  (let [s (name (or shape :none))]
    (cond
      (= s "none") :none
      (.startsWith s "shades_") :shades
      :else :regular)))

(defn glasses-preview-svg [spec shape]
  (let [renderer (render/resolve-renderer :glasses shape)
        color (get-in spec [:parts :other :glasses :color])]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48}
     [:g {:transform "translate(50 50)"}
      (renderer {:color color})]]))

(defn glasses-shape-button [spec shape label]
  (let [selected? (= (get-in spec [:parts :other :glasses :shape]) shape)]
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
      :on-click #(state/swap-spec! assoc-in [:parts :other :glasses :shape] shape)}
     (glasses-preview-svg spec shape)]))

(defn glasses-shape-panel [spec]
  (let [entries (vec (render/sorted-shape-entries :glasses))
        paged (paginate entries (page-get :shape/glasses) 9)]
    [:div
     [:<>
      [pager :shape/glasses (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 68px)"
                     :gap feature-button-gap}}
       (doall
        (for [[k {:keys [label]}] (:items paged)]
          (glasses-shape-button spec k label)))]]]))

(defn glasses-swatch-panel [spec]
  (let [shape (get-in spec [:parts :other :glasses :shape])
        kind (glasses-kind shape)
        selected (get-in spec [:parts :other :glasses :color])
        swatches (if (= kind :shades) cfg/shades-swatches cfg/frame-swatches)
        page-key (if (= kind :shades) :swatch/shades :swatch/frame)
        paged (paginate swatches (page-get page-key) 6)]
    [:div
     [:<>
      [pager page-key (:pages paged)]
      [:div {:style {:display "grid"
                     :grid-template-columns "repeat(3, 32px)"
                     :gap swatch-button-gap}}
       (doall
        (for [swatch (:items paged)]
          ^{:key (:key swatch)}
          [color-swatch-button
           {:selected? (= selected (:key swatch))
            :swatch swatch
            :on-click #(state/swap-spec! assoc-in
                              [:parts :other :glasses :color]
                              (:key swatch))}]))]]]))

(defn glasses-nudge-controls []
  (let [color "black"
        dy (nudge-delta :glasses/y-offset)
        ds (nudge-delta :glasses/scale)]
    [:div {:style {:display "grid" :gap 0}}
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Glasses up"
                 :icon (icons/btn-move-up color)
                 :on-click #(nudge! [:parts :other :glasses :y-offset] (- dy))}]
      [icon-btn {:title "Glasses down"
                 :icon (icons/btn-move-down color)
                 :on-click #(nudge! [:parts :other :glasses :y-offset] dy)}]]
     [:div {:style {:display "flex" :gap 0}}
      [icon-btn {:title "Glasses bigger"
                 :icon (icons/btn-scale-up color)
                 :on-click #(nudge! [:parts :other :glasses :scale] ds)}]
      [icon-btn {:title "Glasses smaller"
                 :icon (icons/btn-scale-down color)
                 :on-click #(nudge! [:parts :other :glasses :scale] (- ds))}]]]))

(def other-subcategory-tabs
  [{:value :glasses :label "Glasses" :icon (icons/icon-glasses "black")}
   {:value :birthmark :label "Birthmark" :icon (icons/icon-birthmark "black")}
   {:value :mustache :label "Mustache" :icon (icons/icon-mustache "black")}
   {:value :beard :label "Beard" :icon (icons/icon-beard "black")}])

(defn other-subcategory-tabs-row []
  [:div {:class "mb2 flex flex-wrap justify-center"}
   (doall
    (for [{:keys [value label icon]} other-subcategory-tabs]
      (let [active? (= (get-in (state/ui) [:other-subcategory]) value)]
        ^{:key (name value)}
        [:button
         {:title label
          :aria-label label
          :on-click #(state/swap-ui! assoc :other-subcategory value)
          :class "pa2 pa0-ns w3-m h3-m w4-l h4-l"
          :style {:display "flex"
                  :align-items "center"
                  :justify-content "center"
                  :border (if active? "2px solid blue" "1px solid gray")
                  :background (if active? "#eef5ff" "white")}}
         icon])))])

(defn other-feature-sections [spec]
  (case (get-in (state/ui) [:other-subcategory])
    :glasses
    {:shape [glasses-shape-panel spec]
     :swatches [glasses-swatch-panel spec]
     :nudge [glasses-nudge-controls]}

    :birthmark {:shape [:div {:style {:font-size 12 :opacity 0.7}} "Birthmark coming soon."]}
    :mustache {:shape [:div {:style {:font-size 12 :opacity 0.7}} "Mustaches coming soon."]}
    :beard {:shape [:div {:style {:font-size 12 :opacity 0.7}} "Beards coming soon."]}
    {:shape [:div {:style {:font-size 12 :opacity 0.7}} "Coming soon."]}))

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
  (let [active? (= (get-in (state/ui) [:active-feature]) value)]
    ^{:key (name value)}
    [:button
     {:title label
      :aria-label label
      :on-click #(state/swap-ui! assoc :active-feature value)
      :class "pa2 pa0-ns w3-m h3-m w4-l h4-l"
      :style {:display "flex"
              :align-items "center"
              :justify-content "center"
              :border (if active? "2px solid blue" "1px solid gray")
              :background (if active? "#eef5ff" "white")}}
     icon]))

(defn feature-tab-buttons-row []
  [:div {:class "flex flex-wrap items-center justify-center"}
   (doall
    (for [tab-btn feature-tab-buttons]
      (feature-tab-btn tab-btn)))])

(defonce !mobile-subpanel (r/atom :shape))

(def mobile-subpanel-tabs
  [{:value :shape :label "Shape"}
   {:value :swatches :label "Color"}
   {:value :nudge :label "Adjust"}])

(defn available-mobile-subpanels [{:keys [shape swatches nudge]}]
  (cond-> []
    shape (conj :shape)
    swatches (conj :swatches)
    nudge (conj :nudge)))

(defn active-mobile-subpanel [sections]
  (let [available (set (available-mobile-subpanels sections))
        current @!mobile-subpanel
        fallback (or (first (available-mobile-subpanels sections)) :shape)]
    (if (contains? available current) current fallback)))

(defn mobile-subpanel-tabs-row [sections]
  (let [available (set (available-mobile-subpanels sections))
        active (active-mobile-subpanel sections)]
    [:div {:class "flex items-center justify-center flex-wrap mb2"}
     (doall
      (for [{:keys [value label]} mobile-subpanel-tabs]
        (let [enabled? (contains? available value)
              selected? (= active value)]
          ^{:key (name value)}
          [:button
           {:title label
            :aria-label label
            :disabled (not enabled?)
            :on-click #(reset! !mobile-subpanel value)
            :class "pa2"
            :style {:opacity (if enabled? 1 0.35)
                    :border (if selected? "2px solid blue" "1px solid gray")
                    :background (if selected? "#eef5ff" "white")}}
           label])))]))

(defn mobile-active-subpanel-content [sections]
  (case (active-mobile-subpanel sections)
    :shape (:shape sections)
    :swatches (:swatches sections)
    :nudge (:nudge sections)
    (:shape sections)))

;; -------------------------
;; Feature Sections
;; -------------------------

(defn active-feature-sections [spec]
  (case (get-in (state/ui) [:active-feature])
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
    :other (merge
            {:prefix [other-subcategory-tabs-row]}
            (other-feature-sections spec))
    {:shape [:div "Select a feature"]}))

(defn preset-button [preset current-spec]
  (let [name-id (or (:name-id preset) "Preset")
        selected? (= (:name-id current-spec) (:name-id preset))]
    ^{:key (str "preset-" name-id)}
    [:button
     {:title (str "Load preset: " name-id)
      :aria-label (str "Load preset: " name-id)
      :on-click #(state/reset-spec! (render/normalize-spec preset))
      :style {:display "flex"
              :flex-direction "column"
              :align-items "center"
              :justify-content "center"
              :width 88
              :height 102
              :padding 4
              :border (if selected? "2px solid #333" "1px solid #ccc")
              :background "#fff"
              :border-radius 8
              :cursor "pointer"}}
     [render/avatar->hiccup preset {:width 72 :height 72}]
     [:span {:style {:font-size 12 :margin-top 2}} name-id]]))

(defn presets-panel []
  (let [current-spec (state/spec)
        entries (mapv (fn [p] [(:name-id p) p]) cfg/presets)
        paged (paginate entries (page-get :preset/page) 9)]
    [:div {:class "mt3"}
     [:div {:style {:font-size 12 :margin-bottom "6px"}} "Presets"]
     [pager :preset/page (:pages paged)]
     [:div {:style {:display "grid"
                    :grid-template-columns "repeat(3, 88px)"
                    :gap 8}}
      (doall
       (for [[_ preset] (:items paged)]
         (preset-button preset current-spec)))]]))

(defn footer-tools-panel []
  (let [show-svg? (get-in (state/ui) [:show-svg?])
        show-edn? (get-in (state/ui) [:show-edn?])
        show-about? (get-in (state/ui) [:show-about?])
        show-presets? (get-in (state/ui) [:show-presets?])
        svg-source (storage/svg-source)
        edn-export (storage/edn-export)]
    [:footer
     {:class "footer bg-white ba b--black-20 br3 pa3 mt2 fixed-ns bottom-0 left-0 right-0"}
     [:div {:class "flex flex-wrap items-center"}
      [:button {:on-click #(state/reset-spec! cfg/default-spec)} "Reset"]
      [:button {:class "ml2"
                :on-click #(state/swap-ui! update :show-svg? not)}
       (if show-svg? "Hide SVG source" "Show SVG source")]
      [:button {:class "ml2"
                :on-click #(state/swap-ui! update :show-edn? not)}
       (if show-edn? "Hide EDN" "Show EDN")]
      [:button {:class "ml2"
                :on-click #(state/swap-ui! update :show-about? not)}
       (if show-about? "Hide About" "About")]
      [:button {:class "ml2"
                :on-click #(state/swap-ui! update :show-presets? not)}
       (if show-presets? "Hide Presets" "Presets")]]

     (when show-presets?
       [presets-panel])

     (when show-about?
       [:div {:class "mt3"}
        [:div {:style {:font-size 12 :line-height 1.5}}
         [:div [:strong "Avatar Maker"] " (v016)"]
         [:div "Author: Avi Drucker"]
         [:div "Built with ClojureScript, shadow-cljs, and Reagent."]
         [:div
          "GitHub: "
          [:a {:href "https://github.com/avidrucker/avatar-maker"
               :target "_blank"
               :rel "noopener noreferrer"}
           "github.com/avidrucker/avatar-maker"]]]])

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
                    :value (get-in (state/ui) [:edn-import-text])
                    :on-input
                    (fn [e]
                      (state/swap-ui! assoc
                                      :edn-import-error nil
                                      :edn-import-text (.. e -target -value)))}]

        [:div {:style {:margin-top "8px"}}
         [:button {:on-click #(storage/load-edn-into-spec!)}
          "Load EDN"]]

        (when (get-in (state/ui) [:edn-import-error])
          [:div {:style {:margin-top "8px"
                         :color "#a00"
                         :font-size 12}}
           (get-in (state/ui) [:edn-import-error])])])]))

;; -------------------------
;; Root UI
;; -------------------------

(defn main-panel []
  (let [spec (state/spec)
        sections (active-feature-sections spec)
        {:keys [prefix shape swatches nudge]} sections]
    [:div {:class "pa1 relative"}

     [:div
      ;; feature tab buttons that render for tablet and desktop 
      {:class "dn db-ns ba b--black-20 br3 mb2"}
      [feature-tab-buttons-row]]

     [:div {:class "feature-tab-buttons-row-mobile ba b--black-20 br3 pa2 db dn-ns mr-auto ml-auto mr0-ns ml0-ns mb2 mb0-ns"}
      [feature-tab-buttons-row]]

     [:div
      {:class "flex flex-column flex-row-ns items-start justify-center-ns"}

      [:div
       {:class "avatar-preview-container w-100 w-50-ns measure-narrow mr-auto ml-auto ba b--black-20 br3 mb2 mb0-ns mr2-ns ml0-ns"
        :style {:flex "0 0 auto"}}
       [render/avatar->hiccup spec]] 

      [:div
       {:class "mobile-subpanel-container w-100 flex-ns flex-column ba b--black-20 br3 pa2 mr-auto ml-auto mr0-ns ml0-ns"}
       [:div {:class "mobile-tabbed-subpanel"}
        (when prefix
          [:div prefix])
        [mobile-subpanel-tabs-row sections]
        [:div {:class "flex items-start justify-center"}
         (mobile-active-subpanel-content sections)]]

       [:div
        {:class "horizontal-tab-container controls-layout measure flex flex-wrap items-start justify-start-ns justify-center mr-auto ml-auto mr0-ns ml0-ns"}
        (when prefix
          [:div {:class "w-100 w-auto-ns"} prefix])
        [:div {:class "shape-pane mr2 pb2"} shape]
        [:div {:class "meta-pane ml2-l"}
         (when swatches
           [:div {:class "mb2"} swatches])
         (when nudge
           [:div nudge])]]]] 

     [footer-tools-panel]]))
