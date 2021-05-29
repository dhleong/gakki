(ns gakki.views.home
  (:require [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.theme :as theme]))

(defn- handle-input [k]
  (case k
    "r" (>evt [:providers/refresh!])

    nil))

(defn- header []
  [:> k/Box {:flex-direction :row
             :justify-content :space-between}
   [:> k/Text {:color theme/header-color-on-background}
    (if (<sub [:loading?])
      [:> Spinner {:type "dots"}]
      " ")
    " Gakki Home"]
   [player-mini]])

(defn view []
  (use-input handle-input)

  [:> k/Box {:flex-direction :column
             :border-color theme/text-color-on-background
             :border-style :round
             :padding-x 1}
   [header]

   [:f> carousels]])
