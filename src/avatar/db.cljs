(ns avatar.db
  (:require [reagent.core :as r]))

;; The single source of truth
(defonce !spec (r/atom nil))

;; UI State
(defonce !active-feature (r/atom :head))
(defonce !ui-pages (r/atom {}))
(defonce !other-subcategory (r/atom :glasses))

;; Persistent/local storage keys
(def storage-key "mii-svg-avatar/v016")
(def show-svg-key "mii-svg-avatar/show-svg")
(def active-feature-key "mii-svg-avatar/active-feature")

;; Footer tool state
(defonce !show-svg? (r/atom false))
(defonce !show-edn? (r/atom false))
(defonce !show-about? (r/atom false))
(defonce !edn-import-text (r/atom ""))
(defonce !edn-import-error (r/atom nil))
