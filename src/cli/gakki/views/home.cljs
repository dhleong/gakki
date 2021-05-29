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

    ; TODO: these should probably be global...
    "p" (>evt [:player/play-pause])
    "[" (>evt [:player/volume-inc -1])
    "]" (>evt [:player/volume-inc 1])

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

   [:f> carousels
    :categories (<sub [:home/categories])
    :navigate-categories! #(>evt [:home/navigate-categories %])
    :navigate-row! #(>evt [:home/navigate-row %])
    :open-selected! #(>evt [:home/open-selected])]])
