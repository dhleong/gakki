(ns gakki.components.header
  (:require [archetype.util :refer [<sub]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.theme :as theme]))

(defn- text? [items]
  (every? #(or (string? %)
               (identical? k/Text
                           (second %)))
          items))

(defn header [& title]
  (let [title-container (if (text? title)
                          [:> k/Text {:color theme/header-color-on-background}]
                          [:> k/Box {:flex-direction :row}])
        title-element (into title-container title)]
    [:> k/Box {:flex-direction :row
               :justify-content :space-between}
     title-element

     [:> k/Box {:flex-direction :row
                :margin-right -1}
      [player-mini]

      (if (<sub [:loading?])
        [:> Spinner {:type "dots"}]
        [:> k/Text " "])]
     ]))
