(ns gakki.views.home
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.header :refer [header]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.theme :as theme]
            [gakki.views.splash :as splash]))

(defn- handle-input [k]
  (case k
    "r" (>evt [:providers/refresh!])

    nil))

(defn- initialized []
  (use-input handle-input)

  [:> k/Box {:flex-direction :column
             :border-color theme/text-color-on-background
             :border-style :round
             :padding-x 1}
   [header "Gakki Home"]

   [:f> carousels]])

(defn view []
  (if (<sub [:initializing?])
    [splash/view]
    [:f> initialized]))
