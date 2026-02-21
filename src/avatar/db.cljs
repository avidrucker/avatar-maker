(ns avatar.db
  (:require [reagent.core :as r]))

;; The single source of truth
(defonce !spec (r/atom nil))

;; UI State
(defonce !active-feature (r/atom :head))
(defonce !ui-pages (r/atom {}))

;; Constants for storage
(def storage-key "mii-svg-avatar/v016")