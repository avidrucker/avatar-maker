(ns avatar.state
  (:require [reagent.core :as r]))

(def default-ui
  {:active-feature :head
   :theme-mode :system
   :ui-pages {}
   :other-subcategory :glasses
   :show-svg? false
   :show-edn? false
   :show-about? false
   :show-presets? false
   :hidden-preset-ids []
   :user-presets []
   :editing-name? false
   :name-draft ""
   :edn-import-text ""
   :edn-import-error nil})

(defonce !app
  (r/atom {:spec nil
           :ui default-ui}))

(defn spec []
  (:spec @!app))

(defn ui []
  (:ui @!app))

(defn swap-spec!
  [f & args]
  (apply swap! !app update :spec f args))

(defn reset-spec!
  [new-spec]
  (swap! !app assoc :spec new-spec))

(defn swap-ui!
  [f & args]
  (apply swap! !app update :ui f args))
