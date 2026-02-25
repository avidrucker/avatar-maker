(ns avatar.db
  (:require [avatar.state :as state]
            [reagent.core :as r]))

;; Compatibility cursors into unified app state.
(defonce !spec (r/cursor state/!app [:spec]))
(defonce !active-feature (r/cursor state/!app [:ui :active-feature]))
(defonce !ui-pages (r/cursor state/!app [:ui :ui-pages]))
(defonce !other-subcategory (r/cursor state/!app [:ui :other-subcategory]))

;; Persistent/local storage keys
(def storage-key "mii-svg-avatar/v016")
(def show-svg-key "mii-svg-avatar/show-svg")
(def show-presets-key "mii-svg-avatar/show-presets")
(def active-feature-key "mii-svg-avatar/active-feature")

;; Footer tool state
(defonce !show-svg? (r/cursor state/!app [:ui :show-svg?]))
(defonce !show-edn? (r/cursor state/!app [:ui :show-edn?]))
(defonce !show-about? (r/cursor state/!app [:ui :show-about?]))
(defonce !show-presets? (r/cursor state/!app [:ui :show-presets?]))
(defonce !edn-import-text (r/cursor state/!app [:ui :edn-import-text]))
(defonce !edn-import-error (r/cursor state/!app [:ui :edn-import-error]))
