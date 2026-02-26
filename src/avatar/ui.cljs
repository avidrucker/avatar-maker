(ns avatar.ui
  (:require [avatar.state :as state]
            [avatar.render :as render]
            [avatar.storage :as storage]
            [avatar.config :as cfg]
            [avatar.ui.components :as comp]
            [avatar.icons :as icons]
            [clojure.string :as str]))

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
  [comp/pager {:page (page-get page-key)
               :pages pages
               :on-prev #(page-prev! page-key)
               :on-next #(page-next! page-key pages)}])

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

(def feature-ui
  {:head {:shape {:page-key :shape/head
                  :per-page 9
                  :entries-fn head-shape-entries
                  :selected-path [:parts :head :shape]
                  :preview-fn (fn [spec shape]
                                (head-preview-svg shape (get-in spec [:parts :head :skin])))}
          :swatch {:page-key :swatch/skin
                   :per-page 12
                   :columns 3
                   :swatches cfg/skin-swatches
                   :selected-path [:parts :head :skin]}}
   :hair {:shape {:page-key :shape/hair
                  :per-page 9
                  :entries-fn #(vec (render/sorted-shape-entries :hair))
                  :selected-path [:parts :hair :shape]
                  :preview-fn hair-preview-svg}
          :swatch {:page-key :swatch/hair
                   :per-page 8
                   :columns 4
                   :swatches cfg/hair-swatches
                   :selected-path [:parts :hair :color]}}
   :eyes {:shape {:page-key :shape/eyes
                  :per-page 9
                  :entries-fn #(vec (render/sorted-shape-entries :eyes))
                  :selected-path [:parts :eyes :shape]
                  :preview-fn (fn [spec shape]
                                (eye-preview-svg shape (get-in spec [:parts :eyes :iris])))}
          :swatch {:page-key :swatch/iris
                   :per-page 6
                   :columns 3
                   :swatches cfg/iris-swatches
                   :selected-path [:parts :eyes :iris]}}
   :brows {:shape {:page-key :shape/brows
                   :per-page 9
                   :entries-fn #(vec (render/sorted-shape-entries :brows))
                   :selected-path [:parts :brows :shape]
                   :preview-fn (fn [spec shape]
                                 (brow-preview-svg shape (get-in spec [:parts :brows :color])))}
           :swatch {:page-key :swatch/brows
                    :per-page 8
                    :columns 4
                    :swatches cfg/hair-swatches
                    :selected-path [:parts :brows :color]}}
   :nose {:shape {:page-key :shape/nose
                  :per-page 9
                  :entries-fn #(vec (render/sorted-shape-entries :nose))
                  :selected-path [:parts :nose :shape]
                  :preview-fn (fn [_ shape] (nose-preview-svg shape))}}
   :mouth {:shape {:page-key :shape/mouth
                   :per-page 9
                   :entries-fn #(vec (render/sorted-shape-entries :mouth))
                   :selected-path [:parts :mouth :shape]
                   :preview-fn (fn [spec shape]
                                 (mouth-preview-svg shape (get-in spec [:parts :mouth :color])))}
           :swatch {:page-key :swatch/lips
                    :per-page 6
                    :columns 3
                    :swatches cfg/lip-swatches
                    :selected-path [:parts :mouth :color]}}
   :glasses {:shape {:page-key :shape/glasses
                     :per-page 9
                     :entries-fn #(vec (render/sorted-shape-entries :glasses))
                     :selected-path [:parts :other :glasses :shape]
                     :preview-fn glasses-preview-svg}
             :swatch {:per-page 6
                      :columns 3
                      :selected-path [:parts :other :glasses :color]
                      :swatches-fn (fn [spec]
                                     (let [shape (get-in spec [:parts :other :glasses :shape])
                                           kind (glasses-kind shape)]
                                       {:page-key (if (= kind :shades) :swatch/shades :swatch/frame)
                                        :swatches (if (= kind :shades) cfg/shades-swatches cfg/frame-swatches)}))}}})

(defn shape-panel [spec feature]
  (let [{:keys [page-key per-page entries-fn selected-path preview-fn]} (get-in feature-ui [feature :shape])
        entries (entries-fn)
        paged (paginate entries (page-get page-key) per-page)
        selected (get-in spec selected-path)]
    [comp/shape-picker
     {:entries entries
      :paged paged
      :page-key page-key
      :page-get page-get
      :on-page-prev page-prev!
      :on-page-next page-next!
      :selected selected
      :on-select #(state/swap-spec! assoc-in selected-path %)
      :render-preview #(preview-fn spec %)
      :item-width 68
      :columns 3
      :gap feature-button-gap}]))

(defn swatch-panel [spec feature]
  (let [{:keys [page-key per-page columns swatches selected-path swatches-fn]} (get-in feature-ui [feature :swatch])
        {:keys [page-key swatches]} (if swatches-fn (swatches-fn spec) {:page-key page-key :swatches swatches})
        paged (paginate swatches (page-get page-key) per-page)
        selected (get-in spec selected-path)]
    [comp/swatch-picker
     {:paged paged
      :page-key page-key
      :page-get page-get
      :on-page-prev page-prev!
      :on-page-next page-next!
      :selected selected
      :on-select #(state/swap-spec! assoc-in selected-path %)
      :columns columns
      :gap swatch-button-gap}]))

(def nudge-specs
  {:eyes
   [{:row 0 :title "Eyes up" :icon icons/btn-move-up :path [:parts :eyes :y-offset] :dir -1 :cfg-key :eyes/y-offset}
    {:row 0 :title "Eyes down" :icon icons/btn-move-down :path [:parts :eyes :y-offset] :dir 1 :cfg-key :eyes/y-offset}
    {:row 1 :title "Eyes bigger" :icon icons/btn-scale-up :path [:parts :eyes :size] :dir 1 :cfg-key :eyes/size}
    {:row 1 :title "Eyes smaller" :icon icons/btn-scale-down :path [:parts :eyes :size] :dir -1 :cfg-key :eyes/size}
    {:row 2 :title "Rotate clockwise" :icon icons/btn-rotate-clockwise :path [:parts :eyes :rotation] :dir 1 :cfg-key :eyes/rotation}
    {:row 2 :title "Rotate counter-clockwise" :icon icons/btn-rotate-counter :path [:parts :eyes :rotation] :dir -1 :cfg-key :eyes/rotation}
    {:row 3 :title "Move eyes together" :icon icons/btn-move-together :path [:parts :eyes :spacing] :dir -1 :cfg-key :eyes/spacing}
    {:row 3 :title "Move eyes apart" :icon icons/btn-move-apart :path [:parts :eyes :spacing] :dir 1 :cfg-key :eyes/spacing}]

   :brows
   [{:row 0 :title "Brows up" :icon icons/btn-move-up :path [:parts :brows :y-offset] :dir -1 :cfg-key :brows/y-offset}
    {:row 0 :title "Brows down" :icon icons/btn-move-down :path [:parts :brows :y-offset] :dir 1 :cfg-key :brows/y-offset}
    {:row 1 :title "Brows bigger" :icon icons/btn-scale-up :path [:parts :brows :size] :dir 1 :cfg-key :brows/size}
    {:row 1 :title "Brows smaller" :icon icons/btn-scale-down :path [:parts :brows :size] :dir -1 :cfg-key :brows/size}
    {:row 2 :title "Rotate clockwise" :icon icons/btn-rotate-clockwise :path [:parts :brows :rotation] :dir 1 :cfg-key :brows/rotation}
    {:row 2 :title "Rotate counter-clockwise" :icon icons/btn-rotate-counter :path [:parts :brows :rotation] :dir -1 :cfg-key :brows/rotation}
    {:row 3 :title "Move brows together" :icon icons/btn-move-together :path [:parts :brows :x-offset] :dir -1 :cfg-key :brows/x-offset}
    {:row 3 :title "Move brows apart" :icon icons/btn-move-apart :path [:parts :brows :x-offset] :dir 1 :cfg-key :brows/x-offset}]

   :nose
   [{:row 0 :title "Nose up" :icon icons/btn-move-up :path [:parts :nose :y-offset] :dir -1 :cfg-key :nose/y-offset}
    {:row 0 :title "Nose down" :icon icons/btn-move-down :path [:parts :nose :y-offset] :dir 1 :cfg-key :nose/y-offset}
    {:row 1 :title "Nose bigger" :icon icons/btn-scale-up :path [:parts :nose :size] :dir 1 :cfg-key :nose/size}
    {:row 1 :title "Nose smaller" :icon icons/btn-scale-down :path [:parts :nose :size] :dir -1 :cfg-key :nose/size}]

   :mouth
   [{:row 0 :title "Mouth up" :icon icons/btn-move-up :path [:parts :mouth :y-offset] :dir -1 :cfg-key :mouth/y-offset}
    {:row 0 :title "Mouth down" :icon icons/btn-move-down :path [:parts :mouth :y-offset] :dir 1 :cfg-key :mouth/y-offset}
    {:row 1 :title "Mouth bigger" :icon icons/btn-scale-up :path [:parts :mouth :size] :dir 1 :cfg-key :mouth/size}
    {:row 1 :title "Mouth smaller" :icon icons/btn-scale-down :path [:parts :mouth :size] :dir -1 :cfg-key :mouth/size}]

   :glasses
   [{:row 0 :title "Glasses up" :icon icons/btn-move-up :path [:parts :other :glasses :y-offset] :dir -1 :cfg-key :glasses/y-offset}
    {:row 0 :title "Glasses down" :icon icons/btn-move-down :path [:parts :other :glasses :y-offset] :dir 1 :cfg-key :glasses/y-offset}
    {:row 1 :title "Glasses bigger" :icon icons/btn-scale-up :path [:parts :other :glasses :scale] :dir 1 :cfg-key :glasses/scale}
    {:row 1 :title "Glasses smaller" :icon icons/btn-scale-down :path [:parts :other :glasses :scale] :dir -1 :cfg-key :glasses/scale}]})

(defn nudge-rows [feature]
  (let [color "black"
        items (get nudge-specs feature)]
    (when (seq items)
      (->> items
           (sort-by :row)
           (group-by :row)
           (map (fn [[_ row-items]]
                  (vec
                   (for [{:keys [title icon path dir cfg-key]} row-items
                         :let [delta (* dir (nudge-delta cfg-key))]]
                     {:title title
                      :icon (icon color)
                      :on-click #(nudge! path delta)}))))
           vec))))

(defn nudge-panel [feature]
  (when-let [rows (nudge-rows feature)]
    [comp/nudge-pad {:rows rows}]))

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
    {:shape [shape-panel spec :glasses]
     :swatches [swatch-panel spec :glasses]
     :nudge [nudge-panel :glasses]}

    :birthmark {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Birthmark coming soon."]}
    :mustache {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Mustaches coming soon."]}
    :beard {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Beards coming soon."]}
    {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Coming soon."]}))

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
        current (get-in (state/ui) [:mobile-subpanel])
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
            :on-click #(do
                         (state/swap-ui! assoc :mobile-subpanel value)
                         (storage/save-mobile-subpanel! value))
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
    :head  {:shape [shape-panel spec :head]
            :swatches [swatch-panel spec :head]}
    :hair  {:shape [shape-panel spec :hair]
            :swatches [swatch-panel spec :hair]}
    :brows {:shape [shape-panel spec :brows]
            :swatches [swatch-panel spec :brows]
            :nudge [nudge-panel :brows]}
    :eyes  {:shape [shape-panel spec :eyes]
            :swatches [swatch-panel spec :eyes]
            :nudge [nudge-panel :eyes]}
    :nose  {:shape [shape-panel spec :nose]
            :nudge [nudge-panel :nose]}
    :mouth {:shape [shape-panel spec :mouth]
            :swatches [swatch-panel spec :mouth]
            :nudge [nudge-panel :mouth]}
    :other (merge
            {:prefix [other-subcategory-tabs-row]}
            (other-feature-sections spec))
    {:shape [:div "Select a feature"]}))

(defn preset-button [preset current-spec]
  (let [name-id (or (:name-id preset) "Preset")
        preset-id (:preset-id preset)
        selected? (= (:preset-id current-spec) preset-id)]
    ^{:key (str "preset-" preset-id)}
    [:div {:class "preset-item"
           :style {:position "relative"
                   :width 88}}
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
      [:span {:style {:font-size 12 :margin-top 2}} name-id]]
     [:button
      {:title (str "Hide preset: " name-id)
       :aria-label (str "Hide preset: " name-id)
       :class "preset-delete-btn bn absolute top-0 right-0 w2 h2 bg-transparent"
       :on-click #(state/swap-ui! update :hidden-preset-ids
                                  (fn [ids]
                                    (let [s (set (or ids []))]
                                      (vec (conj s preset-id)))))
       :style {:cursor "pointer"}}
      "x"]]))

(defn all-presets []
  (vec (concat cfg/presets (get-in (state/ui) [:user-presets]))))

(defn preset-signature
  "Canonical form for duplicate detection; ignore only unique preset id."
  [preset]
  (-> (render/normalize-spec (or preset cfg/default-spec))
      (dissoc :preset-id)))

(defn duplicate-current-preset?
  []
  (let [current-sig (preset-signature (state/spec))]
    (boolean
     (some #(= current-sig (preset-signature %))
           (all-presets)))))

(defn save-current-as-preset! []
  (when-not (duplicate-current-preset?)
    (let [spec (or (state/spec) cfg/default-spec)
          saved-preset (assoc (render/normalize-spec spec)
                              :preset-id (str (random-uuid))
                              :name-id (or (:name-id spec) "Preset"))
          next-current-id (str (random-uuid))]
      (state/swap-ui! update :user-presets
                      (fn [presets]
                        (conj (vec (or presets [])) saved-preset)))
      ;; Keep current avatar distinct from any stored preset ids.
      (state/swap-spec! assoc :preset-id next-current-id))))

(defn restore-presets! []
  (state/swap-ui! assoc :hidden-preset-ids []))

(defn presets-panel []
  (let [current-spec (state/spec)
        hidden-ids (set (get-in (state/ui) [:hidden-preset-ids]))
        all-presets (all-presets)
        visible-presets (->> all-presets
                             (remove #(contains? hidden-ids (:preset-id %)))
                             (sort-by (fn [p] (str/lower-case (or (:name-id p) ""))))
                             vec)
        entries (mapv (fn [p] [(:preset-id p) p]) visible-presets)
        paged (paginate entries (page-get :preset/page) 9)]
    [:div {:class "mt3"}
     [:div {:style {:font-size 12 :margin-bottom "6px"}} "Presets"]
     (if (seq entries)
       [:<>
        [pager :preset/page (:pages paged)]
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(3, 88px)"
                       :gap 8}}
         (doall
          (for [[_ preset] (:items paged)]
            (preset-button preset current-spec)))]]
       [:div {:style {:font-size 12 :opacity 0.7}}
        "No visible presets."])]))

(defn current-name-id [spec]
  (or (:name-id spec) "Unnamed"))

(defn start-name-edit! [spec]
  (state/swap-ui! assoc
                  :editing-name? true
                  :name-draft (current-name-id spec)))

(defn save-name-edit! [spec]
  (let [draft (str/trim (or (get-in (state/ui) [:name-draft]) ""))
        next-name (if (seq draft) draft (current-name-id spec))]
    (state/swap-spec! assoc :name-id next-name)
    (state/swap-ui! assoc :editing-name? false :name-draft "")))

(defn cancel-name-edit! []
  (state/swap-ui! assoc :editing-name? false :name-draft ""))

(defn avatar-name-editor [spec]
  (let [editing? (get-in (state/ui) [:editing-name?])
        display-name (current-name-id spec)]
    (if editing?
      [:input
       {:type "text"
        :value (get-in (state/ui) [:name-draft])
        :auto-focus true
        :max-length 60
        :style {:min-width "110px"
                :max-width "180px"
                :font-size 12}
        :on-change #(state/swap-ui! assoc :name-draft (.. % -target -value))
        :on-blur #(save-name-edit! spec)
        :on-key-down (fn [e]
                       (case (.-key e)
                         "Enter" (do (.preventDefault e)
                                     (.blur (.-target e)))
                         "Escape" (do (.preventDefault e)
                                      (cancel-name-edit!))
                         nil))}]
      [:span
       {:title "Click to rename avatar"
        :style {:font-size 12
                :cursor "text"
                :padding "2px 6px"
                :border "1px solid #ccc"
                :border-radius 6
                :background "white"}
        :on-click #(start-name-edit! spec)}
       display-name])))

(defn footer-tools-panel []
  (let [show-svg? (get-in (state/ui) [:show-svg?])
        show-edn? (get-in (state/ui) [:show-edn?])
        show-about? (get-in (state/ui) [:show-about?])
        show-presets? (get-in (state/ui) [:show-presets?])
        save-disabled? (duplicate-current-preset?)
        svg-source (storage/svg-source)
        edn-export (storage/edn-export)]
    [:footer
     {:class "footer bg-white ba b--black-20 br3 pa1 mt2 fixed-ns bottom-0 left-0 right-0"}
     [:div {:class "flex flex-wrap items-center"}
      [:button {:class "ma1"
                :title "Reset the current avatar back to the default"
                :on-click #(state/reset-spec! cfg/default-spec)} "Reset"]
      [:button {:class "ma1"
                :on-click #(state/swap-ui! update :show-svg? not)}
       (if show-svg? "Hide SVG source" "Show SVG source")]
      [:button {:class "ma1"
                :on-click #(state/swap-ui! update :show-edn? not)}
       (if show-edn? "Hide EDN" "Show EDN")]
      [:button {:class "ma1"
                :on-click #(state/swap-ui! update :show-about? not)}
       (if show-about? "Hide About" "About")]
      [:button {:class "ma1"
                :on-click #(state/swap-ui! update :show-presets? not)}
       (if show-presets? "Hide Presets" "Presets")]
      [:button 
       {:class "ma1"
        :disabled save-disabled?
        :on-click #(save-current-as-preset!)
        :title (if save-disabled?
                 "Change the current avatar to save a new unique one"
                 "Save the current avatar as a preset")
        :style {:opacity (if save-disabled? 0.5 1.0)
                :cursor (if save-disabled? "not-allowed" "pointer")}
        }
       "Save"]
      [:button {:class "ma1"
                :on-click #(restore-presets!)
                :title "Restore all default presets"
                }
       "Restore"]]

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
        {:keys [prefix notice shape swatches nudge]} sections]
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
       {:class "avatar-preview-container relative w-100 w-50-ns measure-narrow mr-auto ml-auto ba b--black-20 br3 mb2 mb0-ns mr2-ns ml0-ns"
        :style {:flex "0 0 auto"}}
       [:div {:class "absolute top-0 right-0 pa2 dn-ns"}
        [avatar-name-editor spec]]
       [render/avatar->hiccup spec]
       [:div {:class "dn db-ns tc pb2"}
        [avatar-name-editor spec]]] 

      [:div
       {:class "mobile-subpanel-container w-100 flex-ns flex-column ba b--black-20 br3 pa2 mr-auto ml-auto mr0-ns ml0-ns"}
       [:div {:class "mobile-tabbed-subpanel"}
        (when prefix
          [:div prefix])
        (when notice
          [:div {:class "notice-text mb2"} notice])
        (when (or shape swatches nudge)
          [mobile-subpanel-tabs-row sections])
        (when (or shape swatches nudge)
          [:div {:class "flex flex-column items-center justify-center"}
           (mobile-active-subpanel-content sections)])]

       [:div
        {:class "horizontal-tab-container controls-layout measure flex flex-wrap items-start justify-start-ns justify-center mr-auto ml-auto mr0-ns ml0-ns"}
        (when prefix
          [:div {:class "w-100 w-auto-ns"} prefix])
        (when notice
          [:div {:class "notice-text w-100 pb2"} notice])
        (when shape
          [:div {:class "shape-pane mr2 pb2"} shape])
        [:div {:class "meta-pane ml2-l"}
         (when swatches
           [:div {:class "mb2"} swatches])
         (when nudge
           [:div nudge])]]]] 

     [footer-tools-panel]]))
