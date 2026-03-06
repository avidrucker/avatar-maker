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
          (get-in render/head-registry [render/head-default :render])))

(def head-preview-icons
  {:001
   [:g
    [:path {:d "M48.6135 9.06827C61.6964 8.42648 69.5268 12.2468 77.6528 23.2021C83.1605 33.1406 84.7505 40.8476 83.8429 47.6234C82.9916 53.9777 79.7544 59.5102 76.4278 64.8594C71.6893 70.434 66.1649 75.3475 60.5523 80.0196C58.0453 82.1064 55.8159 83.8698 54.2117 85.1121C52.7773 86.2229 51.3251 87.2995 49.8656 88.3757C48.167 87.2405 46.4861 86.0999 44.8282 84.9024C42.8637 83.4836 40.1716 81.473 37.2616 79.1046C31.5398 74.4476 24.6161 68.124 20.9537 62.0613C17.4268 56.2226 15.1307 50.33 15.0054 43.8432C14.88 37.3473 16.9373 30.6181 21.4634 23.1058C26.485 14.7711 35.6375 9.70386 48.6135 9.06827ZM72.2613 26.1898C65.6737 18.0107 58.5596 14.6633 48.9717 15.2212C39.2766 15.7855 32.4136 20.2865 26.7435 26.2872C22.6328 33.1101 21.0715 38.7007 21.1684 43.7238C21.2656 48.7558 23.0324 53.5819 26.2298 58.875C29.292 63.944 35.4622 69.6919 41.1523 74.3231C43.9482 76.5987 46.5422 78.5368 48.4369 79.9052C48.9015 80.2407 49.3241 80.5413 49.696 80.8041C49.9234 80.6297 50.1729 80.4431 50.4374 80.2383C51.9974 79.0302 54.1687 77.3131 56.6085 75.2822C61.4617 71.2422 67.2569 66.0551 71.4396 61.2035C74.5737 56.1306 77.0495 51.9072 77.733 46.8058C77.527 41.7468 77.3576 35.3859 72.2613 26.1898Z"
            :fill "currentColor"}]
    [:path {:d "M56.0326 59.2957C57.5313 58.489 59.4003 59.0502 60.2072 60.5487C61.0139 62.0473 60.4527 63.9164 58.9541 64.7233C54.8639 66.9256 46.737 69.3535 39.3826 64.5969C37.9533 63.6725 37.5442 61.7653 38.4686 60.336C39.393 58.9068 41.3002 58.4977 42.7295 59.4221C47.3255 62.3947 52.7996 61.0365 56.0326 59.2957Z"
            :fill "currentColor"}]
    [:circle {:cx 38.4887 :cy 46.5988 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 59.2919 :cy 46.5988 :r 5.13667 :fill "currentColor"}]]
   :002
   [:g
    [:path {:d "M57.0146 59.2957C58.5132 58.489 60.3822 59.0502 61.1891 60.5487C61.9958 62.0473 61.4346 63.9164 59.9361 64.7233C55.8458 66.9256 47.719 69.3535 40.3645 64.5969C38.9353 63.6725 38.5262 61.7653 39.4506 60.336C40.375 58.9068 42.2821 58.4977 43.7114 59.4221C48.3075 62.3947 53.7816 61.0365 57.0146 59.2957Z"
            :fill "currentColor"}]
    [:circle {:cx 39.4707 :cy 46.5988 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.2739 :cy 46.5988 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M81.3683 48.9796C81.3683 31.5752 67.2592 17.4662 49.8549 17.4662C32.4505 17.4662 18.3415 31.5752 18.3415 48.9796C18.3415 66.384 32.4505 80.493 49.8549 80.493C67.2592 80.493 81.3683 66.384 81.3683 48.9796ZM87.0125 48.9796C87.0125 69.5012 70.3764 86.1372 49.8549 86.1372C29.3333 86.1372 12.6973 69.5012 12.6973 48.9796C12.6973 28.458 29.3333 11.822 49.8549 11.822C70.3764 11.822 87.0125 28.458 87.0125 48.9796Z"
            :fill "currentColor"}]]
   :003
   [:g
    [:path {:d "M57.5375 58.6536C59.0361 57.8469 60.9052 58.4081 61.7121 59.9066C62.5187 61.4053 61.9575 63.2743 60.459 64.0812C56.3688 66.2835 48.2419 68.7114 40.8875 63.9548C39.4582 63.0304 39.0491 61.1232 39.9735 59.694C40.8979 58.2647 42.8051 57.8556 44.2343 58.78C48.8304 61.7526 54.3045 60.3945 57.5375 58.6536Z"
            :fill "currentColor"}]
    [:circle {:cx 39.9931 :cy 45.9572 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.7968 :cy 45.9572 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M80.0103 42.6937C80.0103 25.0761 67.5776 11.1803 50.3782 11.1803C33.1789 11.1803 20.7462 25.0761 20.7462 42.6937C20.7462 51.599 23.9445 62.4842 29.422 71.1021C34.9416 79.786 42.306 85.4955 50.3782 85.4955C58.4505 85.4955 65.8149 79.786 71.3345 71.1021C76.812 62.4842 80.0103 51.599 80.0103 42.6937ZM85.6544 42.6937C85.6544 52.751 82.107 64.6768 76.0986 74.13C70.1319 83.5173 61.269 91.1397 50.3782 91.1397C39.4875 91.1397 30.6246 83.5173 24.6579 74.13C18.6495 64.6768 15.1021 52.751 15.1021 42.6937C15.1021 22.3854 29.6516 5.53613 50.3782 5.53613C71.1049 5.53613 85.6544 22.3854 85.6544 42.6937Z"
            :fill "currentColor"}]]
   :004
   [:g
    [:path {:d "M57.0146 58.6536C58.5132 57.8469 60.3822 58.4081 61.1891 59.9066C61.9958 61.4053 61.4346 63.2743 59.9361 64.0812C55.8458 66.2835 47.719 68.7114 40.3645 63.9548C38.9353 63.0304 38.5262 61.1232 39.4506 59.694C40.375 58.2647 42.2821 57.8556 43.7114 58.78C48.3075 61.7526 53.7816 60.3945 57.0146 58.6536Z"
            :fill "currentColor"}]
    [:circle {:cx 39.4707 :cy 45.9572 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.2739 :cy 45.9572 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M49.8543 9.29907C59.7949 9.29907 68.8007 11.8564 75.2661 18.8512C81.585 25.6879 85.0176 36.2771 85.125 51.3678C86.9343 54.6548 88.6222 58.3819 89.4537 62.1876C90.344 66.263 90.3022 70.6499 88.1456 74.6592C85.9804 78.6843 81.9346 81.8631 75.7658 84.0203C69.6252 86.1676 61.1899 87.3771 49.8543 87.3771C38.5189 87.377 30.0834 86.1677 23.9429 84.0203C17.7744 81.8631 13.7282 78.6842 11.5631 74.6592C9.4067 70.65 9.36655 66.2629 10.2568 62.1876C11.0884 58.3815 12.7742 54.6531 14.5837 51.3659C14.6913 36.2759 18.1255 25.6876 24.4445 18.8512C30.9097 11.8566 39.9141 9.29914 49.8543 9.29907ZM49.8543 14.9433C40.8323 14.9433 33.6103 17.2502 28.5894 22.682C23.5241 28.1621 20.2223 37.3313 20.2223 52.1009V52.8431L19.8567 53.4899C18.0389 56.7071 16.4857 60.1197 15.7706 63.3929C15.0587 66.6513 15.2227 69.5466 16.5349 71.986C17.8386 74.4095 20.5249 76.8472 25.8059 78.694C31.1153 80.5507 38.8486 81.7328 49.8543 81.7329C60.8599 81.7329 68.5934 80.5506 73.9028 78.694C79.1836 76.8473 81.87 74.4094 83.1738 71.986C84.4859 69.5467 84.6518 66.6513 83.94 63.3929C83.2249 60.1195 81.6699 56.7072 79.852 53.4899L79.4864 52.8431V52.1009C79.4864 37.3315 76.1863 28.1621 71.1211 22.682C66.1002 17.2498 58.8768 14.9433 49.8543 14.9433Z"
            :fill "currentColor"}]]
   :005
   [:g
    [:path {:d "M57.5375 58.5335C59.0361 57.7268 60.9052 58.288 61.7121 59.7865C62.5187 61.2851 61.9575 63.1542 60.459 63.9611C56.3688 66.1634 48.2419 68.5913 40.8875 63.8347C39.4582 62.9103 39.0491 61.0031 39.9735 59.5738C40.8979 58.1446 42.8051 57.7355 44.2343 58.6599C48.8304 61.6325 54.3045 60.2743 57.5375 58.5335Z"
            :fill "currentColor"}]
    [:circle {:cx 39.9931 :cy 45.8369 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.7968 :cy 45.8369 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M79.0696 43.9928C79.0696 26.2591 67.4725 12.4794 50.3782 12.4794C33.1789 12.4794 20.7462 26.3751 20.7462 43.9928C20.7462 48.6757 20.8758 55.82 21.0053 61.8146C21.07 64.8076 21.1332 67.5073 21.1817 69.4578C21.1949 69.9893 21.2061 70.4651 21.2166 70.8762L50.167 84.61L79.0696 70.8982V43.9928ZM84.7137 74.4681L50.167 90.8587L15.6679 74.492L15.622 72.7594L15.6202 72.7558V72.7043C15.6192 72.6675 15.6166 72.6124 15.6147 72.5408C15.6109 72.3972 15.6052 72.185 15.5981 71.9124C15.5839 71.3665 15.5636 70.575 15.5393 69.5974C15.4907 67.6426 15.4259 64.9379 15.3611 61.9377C15.2316 55.9454 15.1021 48.7442 15.1021 43.9928C15.1021 23.6845 29.6516 6.83521 50.3782 6.83521C71.2099 6.83521 84.7137 23.8006 84.7137 43.9928V74.4681Z"
            :fill "currentColor"}]]
   :006
   [:g
    [:path {:d "M57.0146 60.4148C58.5132 59.6082 60.3822 60.1693 61.1891 61.6679C61.9958 63.1665 61.4346 65.0355 59.9361 65.8424C55.8458 68.0448 47.719 70.4726 40.3645 65.716C38.9353 64.7916 38.5262 62.8844 39.4506 61.4552C40.375 60.0259 42.2821 59.6168 43.7114 60.5412C48.3075 63.5138 53.7816 62.1557 57.0146 60.4148Z"
            :fill "currentColor"}]
    [:circle {:cx 39.4707 :cy 47.7185 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.2739 :cy 47.7185 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M81.8387 47.6179C81.8387 31.5218 67.7343 18.1237 49.855 18.1237C31.9757 18.1237 17.8712 31.5218 17.8712 47.6179C17.8713 55.9991 19.5695 64.3453 21.2978 70.6485C22.1585 73.7876 23.0198 76.3917 23.6624 78.2053C23.7995 78.5922 23.9278 78.9425 24.0427 79.2544H75.6672C75.7822 78.9425 75.9105 78.5922 76.0476 78.2053C76.6902 76.3917 77.5514 73.7876 78.4122 70.6485C80.1404 64.3453 81.8387 55.9991 81.8387 47.6179ZM87.4829 47.6179C87.4829 56.6946 85.6535 65.5783 83.8543 72.1404C82.9513 75.4335 82.0486 78.1706 81.3684 80.0904C81.0282 81.0505 80.7434 81.8081 80.5416 82.33C80.4408 82.5907 80.3605 82.7933 80.3046 82.9327C80.2767 83.0023 80.2539 83.0565 80.2385 83.0944C80.2309 83.113 80.2245 83.1277 80.2201 83.1384C80.218 83.1435 80.2159 83.1481 80.2146 83.1513L80.2127 83.1568V83.1587L79.4907 84.8986H20.2193L19.4973 83.1587V83.1568L19.4954 83.1513C19.4941 83.1481 19.492 83.1435 19.4899 83.1384C19.4855 83.1277 19.4791 83.113 19.4715 83.0944C19.4561 83.0565 19.4333 83.0023 19.4054 82.9327C19.3495 82.7933 19.2691 82.5907 19.1684 82.33C18.9666 81.8081 18.6818 81.0505 18.3416 80.0904C17.6614 78.1706 16.7587 75.4335 15.8557 72.1404C14.0565 65.5783 12.2271 56.6946 12.2271 47.6179C12.2271 28.0189 29.2587 12.4795 49.855 12.4795C70.4513 12.4795 87.4829 28.0189 87.4829 47.6179Z"
            :fill "currentColor"}]]
   :007
   [:g
    [:path {:d "M57.5375 60.3701C59.0361 59.5635 60.9052 60.1247 61.7121 61.6232C62.5187 63.1218 61.9575 64.9908 60.459 65.7977C56.3688 68.0001 48.2419 70.428 40.8875 65.6713C39.4582 64.7469 39.0491 62.8398 39.9735 61.4105C40.8979 59.9813 42.8051 59.5722 44.2343 60.4965C48.8304 63.4691 54.3045 62.111 57.5375 60.3701Z"
            :fill "currentColor"}]
    [:circle {:cx 39.9931 :cy 47.6738 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.7968 :cy 47.6738 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M79.0696 44.4103C79.0696 26.6766 67.4725 12.8969 50.3782 12.8969C33.1789 12.8969 20.7462 26.7926 20.7462 44.4103C20.7462 49.0865 20.8741 54.816 21.0035 59.3916C21.068 61.6762 21.1333 63.6679 21.1817 65.0873C21.1986 65.5821 21.2135 66.0074 21.2258 66.3513L39.9001 87.6842H60.838L79.0696 66.412V44.4103ZM84.7137 68.501L63.4341 93.3284H37.3389L16.3183 69.3167L15.6606 68.5634L15.622 67.564V67.5548C15.6218 67.548 15.6207 67.5368 15.6202 67.5235C15.6192 67.4972 15.6184 67.4584 15.6165 67.4078C15.6127 67.3049 15.6071 67.152 15.6 66.9558C15.5857 66.5634 15.5655 65.992 15.5412 65.2783C15.4925 63.8506 15.4279 61.8489 15.3629 59.5515C15.2332 54.9622 15.1021 49.1684 15.1021 44.4103C15.1021 24.102 29.6516 7.25269 50.3782 7.25269C71.2099 7.25269 84.7137 24.218 84.7137 44.4103V68.501Z"
            :fill "currentColor"}]]
   :008
   [:g
    [:path {:d "M57.0146 60.3701C58.5132 59.5635 60.3822 60.1247 61.1891 61.6232C61.9958 63.1218 61.4346 64.9908 59.9361 65.7977C55.8458 68.0001 47.719 70.428 40.3645 65.6713C38.9353 64.7469 38.5262 62.8398 39.4506 61.4105C40.375 59.9813 42.2821 59.5722 43.7114 60.4965C48.3075 63.4691 53.7816 62.111 57.0146 60.3701Z"
            :fill "currentColor"}]
    [:circle {:cx 39.4707 :cy 47.6738 :r 5.13667 :fill "currentColor"}]
    [:circle {:cx 60.2739 :cy 47.6738 :r 5.13667 :fill "currentColor"}]
    [:path {:d "M79.4868 44.4103C79.4868 26.7926 67.0541 12.8969 49.8548 12.8969C32.6555 12.8969 20.2228 26.7926 20.2228 44.4103C20.2228 63.0904 23.5847 73.6446 28.6579 79.5487C33.614 85.3166 40.7113 87.2121 49.8548 87.2121C58.9983 87.2121 66.0956 85.3166 71.0518 79.5487C76.1249 73.6446 79.4868 63.0904 79.4868 44.4103ZM85.131 44.4103C85.131 63.507 81.7458 75.7633 75.3327 83.227C68.8025 90.8267 59.6743 92.8563 49.8548 92.8563C40.0353 92.8563 30.9071 90.8267 24.3769 83.227C17.9638 75.7633 14.5786 63.507 14.5786 44.4103C14.5786 24.102 29.1282 7.25269 49.8548 7.25269C70.5814 7.25269 85.131 24.102 85.131 44.4103Z"
            :fill "currentColor"}]]})

(defn head-preview-svg [shape skin-key]
  (if-let [icon (get head-preview-icons shape)]
    [:svg {:viewBox "0 0 100 100"
           :width 48
           :height 48
           :preserveAspectRatio "xMidYMid meet"
           :style {:display "block"
                   :color "var(--icon-color)"}}
     icon]
    (let [head-fn (resolve-head-renderer shape)]
      [:svg {:viewBox "-50 -50 100 100"
             :width 48
             :height 48
             :preserveAspectRatio "xMidYMid meet"
             :style {:display "block"}}
       [head-fn {:skin skin-key}]])))

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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
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

(defn next-theme-mode [theme-mode]
  (case theme-mode
    :system :light
    :light :dark
    :dark :system
    :system))

(defn theme-button-label [theme-mode]
  (str "Theme: " (str/capitalize (name (or theme-mode :system)))))

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
            :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
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
        hair-hex (get cfg/hair-colors color-key)
        layers (hair-fn {:color hair-hex})]
    [:svg {:viewBox "-50 -50 100 100"
           :width 48
           :height 48
           :preserveAspectRatio "xMidYMid meet"
           :style {:display "block"}}
     (:back layers)
     [head-fn {:skin skin-key}]
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
                              "2px solid var(--selected-border-color)"
                              "1px solid var(--border-color)")
                    :background "var(--surface-color)"
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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
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
            :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
            :background "var(--surface-color)"
            :color "var(--icon-color)"
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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
              :border-radius 10
              :cursor "pointer"}
      :on-click #(state/swap-spec! assoc-in [:parts :brows :shape] shape)}
     (brow-preview-svg shape brow-color)]))

(defn brows-nudge-controls []
  (let [color "currentColor"
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
  (let [color "currentColor"
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
  (let [nose-fn (render/resolve-renderer :nose shape)
        {:preview/keys [view-box transform]} (meta nose-fn)]
    [:svg {:viewBox (or view-box "0 0 100 100")
           :width 48
           :height 48
           :style {:display "block"
                   :color "var(--icon-color)"}}
     (if transform
       [:g {:transform transform}
        (nose-fn {:mode :preview :fill "currentColor"})]
       (nose-fn {:mode :preview :fill "currentColor"}))]))

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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
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
  (let [color "currentColor"
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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
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
  (let [color "currentColor"
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
              :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background "var(--surface-color)"
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
  (let [color "currentColor"
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

(def birthmark-preview-scale
  1.5)

(defn birthmark-preview-node
  [shape size x-offset y-offset]
  (render/birthmark-node {:shape shape
                          :size size
                          :x-offset x-offset
                          :y-offset y-offset
                          :scale-factor birthmark-preview-scale}))

(defn birthmark-preview-svg [spec shape]
  (let [head-shape (get-in spec [:parts :head :shape])
        skin-key (get-in spec [:parts :head :skin])
        birthmark (get-in spec [:parts :other :birthmark])
        head-fn (resolve-head-renderer head-shape)]
    [:svg {:viewBox "-50 -50 100 100"
           :width 48
           :height 48
           :preserveAspectRatio "xMidYMid meet"
           :style {:display "block"}}
     [head-fn {:skin skin-key}]
     (birthmark-preview-node shape
                             (:size birthmark)
                             0
                             0)]))

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
                  :fill-with-placeholders? true
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
                  :fill-with-placeholders? true
                  :entries-fn #(vec (render/sorted-shape-entries :nose))
                  :selected-path [:parts :nose :shape]
                  :preview-fn (fn [_ shape] (nose-preview-svg shape))}}
   :mouth {:shape {:page-key :shape/mouth
                   :per-page 9
                   :fill-with-placeholders? true
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
                                       {:page-key (if (= kind :shades)
                                                    :swatch/shades
                                                    :swatch/frame)
                                        :swatches (if (= kind :shades)
                                                    cfg/shades-swatches
                                                    cfg/frame-swatches)}))}}
   :birthmark {:shape {:page-key :shape/birthmark
                       :per-page 9
                       :entries-fn #(vec (render/sorted-shape-entries :birthmark))
                       :selected-path [:parts :other :birthmark :shape]
                       :preview-fn birthmark-preview-svg}}})

(defn shape-panel [spec feature]
  (let [{:keys [page-key per-page entries-fn selected-path preview-fn fill-with-placeholders?]} (get-in feature-ui [feature :shape])
        entries (entries-fn)
        paged (paginate entries (page-get page-key) per-page)
        selected (get-in spec selected-path)
        placeholder-count (if fill-with-placeholders?
                            (max 0 (- per-page (count (:items paged))))
                            0)]
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
      :gap feature-button-gap
      :placeholder-count placeholder-count}]))

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
    {:row 1 :title "Glasses smaller" :icon icons/btn-scale-down :path [:parts :other :glasses :scale] :dir -1 :cfg-key :glasses/scale}]

   :birthmark
   [{:row 0 :title "Birthmark up" :icon icons/btn-move-up :path [:parts :other :birthmark :y-offset] :dir -1 :cfg-key :birthmark/y-offset}
    {:row 0 :title "Birthmark down" :icon icons/btn-move-down :path [:parts :other :birthmark :y-offset] :dir 1 :cfg-key :birthmark/y-offset}
    {:row 1 :title "Birthmark bigger" :icon icons/btn-scale-up :path [:parts :other :birthmark :size] :dir 1 :cfg-key :birthmark/size}
    {:row 1 :title "Birthmark smaller" :icon icons/btn-scale-down :path [:parts :other :birthmark :size] :dir -1 :cfg-key :birthmark/size}
    {:row 2 :title "Birthmark left" :icon icons/btn-move-left :path [:parts :other :birthmark :x-offset] :dir -1 :cfg-key :birthmark/x-offset}
    {:row 2 :title "Birthmark right" :icon icons/btn-move-right :path [:parts :other :birthmark :x-offset] :dir 1 :cfg-key :birthmark/x-offset}]})

(defn nudge-rows [feature]
  (let [color "currentColor"
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
  [{:value :glasses :label "Glasses" :icon (icons/icon-glasses "currentColor")}
   {:value :birthmark :label "Birthmark" :icon (icons/icon-birthmark "currentColor")}
   {:value :mustache :label "Mustache" :icon (icons/icon-mustache "currentColor")}
   {:value :beard :label "Beard" :icon (icons/icon-beard "currentColor")}])

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
                  :color "var(--icon-color)"
                  :border (if active? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
                  :background (if active? "var(--accent-soft-color)" "var(--surface-color)")}}
         icon])))])

(defn other-feature-sections [spec]
  (case (get-in (state/ui) [:other-subcategory])
    :glasses
    {:shape [shape-panel spec :glasses]
     :swatches [swatch-panel spec :glasses]
     :nudge [nudge-panel :glasses]}

    :birthmark {:shape [shape-panel spec :birthmark]
                :nudge [nudge-panel :birthmark]}
    :mustache {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Mustaches coming soon."]}
    :beard {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Beards coming soon."]}
    {:notice [:div {:style {:font-size 12 :opacity 0.7}} "Coming soon."]}))

;; -------------------------
;; Feature category tabs
;; -------------------------

(def feature-tab-buttons
  ;; Simple + friendly for now; we can swap in SVG icons later (like v015).
  [{:value :head  :label "Head"  :icon (icons/icon-head "currentColor")}
   {:value :hair  :label "Hair"  :icon (icons/icon-hair "currentColor")}
   {:value :brows :label "Brows" :icon (icons/icon-brows "currentColor")}
   {:value :eyes  :label "Eyes"  :icon (icons/icon-eyes "currentColor")}
   {:value :nose  :label "Nose"  :icon (icons/icon-nose "currentColor")}
   {:value :mouth :label "Mouth" :icon (icons/icon-mouth "currentColor")}
   {:value :other :label "Other" :icon (icons/icon-other "currentColor")}])

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
              :color "var(--icon-color)"
              :border (if active? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
              :background (if active? "var(--accent-soft-color)" "var(--surface-color)")}}
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
                    :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
                    :background (if selected? "var(--accent-soft-color)" "var(--surface-color)")
                    :color "var(--text-color)"}}
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
               :border (if selected? "2px solid var(--selected-border-color)" "1px solid var(--border-color)")
               :background "var(--surface-color)"
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

(defn preset-empty-tile [idx]
  ^{:key (str "preset-empty-" idx)}
  [:div {:style {:width 88
                 :height 102
                 :border "1px solid var(--border-color)"
                 :border-radius 8
                 :opacity 0.5
                 :background "transparent"}}])

(def preset-tiles-per-page 9)

(defn all-presets []
  (vec (concat cfg/presets (get-in (state/ui) [:user-presets]))))

(defn preset-id-set
  []
  (->> (all-presets)
       (keep :preset-id)
       set))

(defn next-unique-preset-id
  []
  (let [used-ids (preset-id-set)]
    (loop [attempt 0]
      (let [candidate (str (random-uuid))]
        (cond
          (not (contains? used-ids candidate)) candidate
          (>= attempt 50) (str (random-uuid))
          :else (recur (inc attempt)))))))

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
          saved-preset-id (next-unique-preset-id)
          saved-preset (assoc (render/normalize-spec spec)
                              :preset-id saved-preset-id
                              :name-id (or (:name-id spec) "Preset"))
          next-current-id (next-unique-preset-id)]
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
        paged (paginate entries (page-get :preset/page) preset-tiles-per-page)
        filler-count (max 0 (- preset-tiles-per-page (count (:items paged))))]
    [:div {:class "mt3"}
     [:div {:style {:font-size 12 :margin-bottom "6px"}} "Presets"]
     (if (seq entries)
       [:<>
        [pager :preset/page (:pages paged)]
        [:div {:style {:display "grid"
                       :grid-template-columns "repeat(3, 88px)"
                       :gap 8}}
         (doall
          (concat
           (for [[_ preset] (:items paged)]
             (preset-button preset current-spec))
           (for [idx (range filler-count)]
             (preset-empty-tile idx))))]]
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
                :border "1px solid var(--border-color)"
                :border-radius 6
                :background "var(--surface-color)"}
        :on-click #(start-name-edit! spec)}
       display-name])))

(defn footer-tools-panel []
  (let [show-svg? (get-in (state/ui) [:show-svg?])
        show-edn? (get-in (state/ui) [:show-edn?])
        show-about? (get-in (state/ui) [:show-about?])
        show-presets? (get-in (state/ui) [:show-presets?])
        theme-mode (get-in (state/ui) [:theme-mode])
        footer-open? (boolean (or show-svg? show-edn? show-about? show-presets?))
        save-disabled? (duplicate-current-preset?)
        svg-source (storage/svg-source)
        edn-export (storage/edn-export)]
    [:footer
     {:class "footer themed-panel br3 pa1 mt2 fixed-ns bottom-0 left-0 right-0"}
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
      [:button {:class "ma1"
                :title "Cycle theme mode between system, light, and dark"
                :on-click #(state/swap-ui! update :theme-mode next-theme-mode)}
       (theme-button-label theme-mode)] 
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
       "Restore"]
      [:button {:class "ma1"
                :disabled (not footer-open?)
                :title (if footer-open?
                         "Close all footer panels"
                         "No footer panels are open")
                :style {:opacity (if footer-open? 1.0 0.5)
                        :cursor (if footer-open? "pointer" "not-allowed")}
                :on-click #(state/swap-ui! assoc
                                           :show-svg? false
                                           :show-edn? false
                                           :show-about? false
                                           :show-presets? false)}
       "Close"]]

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
                         :color "var(--danger-color, #c53030)"
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
      {:class "dn db-ns themed-panel br3 mb2"}
      [feature-tab-buttons-row]]

     [:div {:class "feature-tab-buttons-row-mobile themed-panel br3 pa2 db dn-ns mr-auto ml-auto mr0-ns ml0-ns mb2 mb0-ns"}
      [feature-tab-buttons-row]]

     [:div
      {:class "flex flex-column flex-row-ns items-start justify-center-ns"}

      [:div
       {:class "avatar-preview-container themed-panel relative w-100 w-50-ns measure-narrow mr-auto ml-auto br3 mb2 mb0-ns mr2-ns ml0-ns"
        :style {:flex "0 0 auto"}}
       [:div {:class "absolute top-0 right-0 pa2 dn-ns"}
        [avatar-name-editor spec]]
       [render/avatar->hiccup spec]
       [:div {:class "dn db-ns tc pb2"}
        [avatar-name-editor spec]]] 

      [:div
       {:class "mobile-subpanel-container themed-panel w-100 flex-ns flex-column br3 pa2 mr-auto ml-auto mr0-ns ml0-ns"}
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
