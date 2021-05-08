(ns gakki.views.home
  (:require [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.theme :as theme]))

(defn- handle-input [input _k]
  (case input
    "r" (>evt [:providers/refresh!])

    nil ; ignore, for now
    ))

(defn view []
  (k/useInput handle-input)

  [:> k/Box {:flex-direction :column
             :border-color theme/text-color-on-background
             :border-style :round
             :padding-x 1}
   [:> k/Text
    (if (<sub [:loading?])
      [:> Spinner {:type "dots"}]
      " ")
    " Gakki Home"]])
