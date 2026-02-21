(ns avatar.core
  (:require [reagent.dom.client :as rdomc]
            [avatar.db :as db]
            [avatar.config :as cfg]
            [avatar.ui :as ui]))

;; Keep the root around across reloads
(defonce root
  (rdomc/create-root (.getElementById js/document "app")))

(defn render! []
  (rdomc/render root [ui/main-panel]))

(defn init! []
  ;; Load from localstorage or use default
  (reset! db/!spec cfg/default-spec)
  (render!))

(defn ^:after-load rerender []
  (render!))
