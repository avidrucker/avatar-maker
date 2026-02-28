(ns avatar.ui.components)

(defn pager
  [{:keys [page pages on-prev on-next]}]
  (when (> pages 1)
    (let [p (or page 0)
          prev-disabled? (<= p 0)
          next-disabled? (>= p (dec pages))]
      [:div {:class "pb2"
             :style {:display "flex"
                     :align-items "center"
                     :gap "8px"}}
       [:button {:disabled prev-disabled?
                 :title "Previous"
                 :aria-label "Previous"
                 :style {:width 28
                         :height 24
                         :opacity (if prev-disabled? 0.35 1)}
                 :on-click on-prev}
        "◀"]
       [:div {:style {:font-size 12}}
        (str (inc p) "/" pages)]
       [:button {:disabled next-disabled?
                 :title "Next"
                 :aria-label "Next"
                 :style {:width 28
                         :height 24
                         :opacity (if next-disabled? 0.35 1)}
                 :on-click on-next}
        "▶"]])))

(defn grid
  [{:keys [columns item-width gap children]}]
  [:div {:style {:display "grid"
                 :grid-template-columns (str "repeat(" columns ", " item-width "px)")
                 :gap (or gap 6)}}
   children])

(defn selectable-tile-button
  [{:keys [title selected? on-click width height child]}]
  [:button
   {:title title
    :aria-label title
    :style {:display "flex"
            :align-items "center"
            :justify-content "center"
            :width width
            :height height
            :padding 6
            :border (if selected? "2px solid #333" "1px solid #ccc")
            :background "#fff"
            :border-radius 10
            :cursor "pointer"}
    :on-click on-click}
   child])

(defn swatch-button
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

(defn shape-picker
  [{:keys [entries paged page-key page-get on-page-prev on-page-next
           selected on-select render-preview item-width columns gap]}]
  (let [page (page-get page-key)]
    [:<>
     [pager {:page page
             :pages (:pages paged)
             :on-prev #(on-page-prev page-key)
             :on-next #(on-page-next page-key (:pages paged))}]
     [grid {:columns (or columns 3)
            :item-width (or item-width 68)
            :gap (or gap 6)
            :children
            (doall
             (for [[k {:keys [label]}] (:items paged)]
               ^{:key (name k)}
               [selectable-tile-button
                {:title label
                 :selected? (= selected k)
                 :width (or item-width 68)
                 :height (or item-width 68)
                 :on-click #(on-select k)
                 :child (render-preview k)}]))}]]))

(defn swatch-picker
  [{:keys [paged page-key page-get on-page-prev on-page-next
           selected on-select columns gap]}]
  (let [page (page-get page-key)]
    [:<>
     [pager {:page page
             :pages (:pages paged)
             :on-prev #(on-page-prev page-key)
             :on-next #(on-page-next page-key (:pages paged))}]
     [grid {:columns (or columns 3)
            :item-width 32
            :gap (or gap 6)
            :children
            (doall
             (for [swatch (:items paged)]
               ^{:key (:key swatch)}
               [swatch-button
                {:selected? (= selected (:key swatch))
                 :swatch swatch
                 :on-click #(on-select (:key swatch))}]))}]]))

(defn nudge-pad
  [{:keys [rows]}]
  [:div {:style {:display "grid" :gap 0}}
   (doall
    (for [[idx row] (map-indexed vector rows)]
      ^{:key idx}
      [:div {:style {:display "flex" :gap 0}}
       (doall
        (for [{:keys [title icon on-click selected?]} row]
          ^{:key title}
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
           [:div icon]]))]))])
