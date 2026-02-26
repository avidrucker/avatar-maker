(ns avatar.core
  (:require [reagent.dom.client :as rdomc]
            [avatar.storage :as storage]
            [avatar.ui :as ui]))

;; Keep the root around across reloads
(defonce root
  (rdomc/create-root (.getElementById js/document "app")))

(defn localhost?
  []
  (contains? #{"localhost" "127.0.0.1" "::1" "[::1]"}
             (.-hostname js/location)))

(defn set-page-title! []
  (set! (.-title js/document)
        (if (localhost?)
          "Avatar Maker (Dev)"
          "Avatar Maker")))

(defn render! []
  (rdomc/render root [ui/main-panel]))

(defn init! []
  ;; Load from localStorage and initialize persistence watchers.
  (set-page-title!)
  (storage/init!)
  (render!))

(defn ^:dev/after-load rerender []
  (set-page-title!)
  (render!))
