(ns avatar.core
  (:require [reagent.dom.client :as rdomc]
            [avatar.storage :as storage]
            [avatar.ui :as ui]))

;; Keep the root around across reloads
(defonce root
  (rdomc/create-root (.getElementById js/document "app")))

(defn render! []
  (rdomc/render root [ui/main-panel]))

(defn init! []
  ;; Load from localStorage and initialize persistence watchers.
  (storage/init!)
  (render!))

(defn ^:dev/after-load rerender []
  (render!))
