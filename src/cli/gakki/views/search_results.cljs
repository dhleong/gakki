(ns gakki.views.search-results
  (:require [archetype.util :refer [<sub]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.components.carousels :refer [carousels]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]
            [gakki.components.limited-text :refer [limited-text]]
            [gakki.theme :as theme]))

(defn- error-view []
  (let [err (<sub [:search/error])]
    [:> k/Box {:flex-direction :column
               :padding-top 1}
     [:> k/Text {:color theme/negative-color
                 :background-color theme/text-color-on-background
                 :inverse true} " Error "]
     [:> k/Text (str "-" (nil? err) (type err) err)]]))

(defn- empty-view []
  [:> k/Box {:padding-top 1}
   [:> k/Text {:color theme/text-color-disabled}
    "No results."]])

(defn- loading-view []
  [:> k/Box {:flex-direction :row
             :padding-top 1}
   [:> Spinner {:type "dots"}]
   [:> k/Text " Searching..."]])

(defn- results-view []
  [:f> carousels])

(defn view [query]
  [frame
   [header
    [:> k/Text {:color theme/text-color-disabled} "Search / "]
    [limited-text {} \" query \"]]

   (case (<sub [:search/state])
     :error [error-view]
     :empty [empty-view]
     :loading [loading-view]
     :ready [results-view])])
