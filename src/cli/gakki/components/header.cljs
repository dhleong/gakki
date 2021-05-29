(ns gakki.components.header
  (:require [archetype.util :refer [<sub]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.theme :as theme]))

(defn header [& title]
  [:> k/Box {:flex-direction :row
             :justify-content :space-between}
   (into
     [:> k/Text {:color theme/header-color-on-background}
      (when (<sub [:loading?])
        [:> Spinner {:type "dots"}])]
     title)
   [player-mini]])
