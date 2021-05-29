(ns gakki.views.queue
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.header :refer [header]]
            [gakki.theme :as theme]))

(defn view []
  (use-input
    (fn [k]
      (case k
        :escape (>evt [:navigate/back!])
        nil)))

  [:> k/Box {:flex-direction :column
             :border-color theme/text-color-on-background
             :border-style :round
             :padding-x 1}
   [header
    [:> k/Text {:color theme/text-color-disabled}
     "Gakki Queue"]]

   (let [queue (<sub [:player/queue])]

     [:> k/Text "Length: " (count queue)])])
