(ns gakki.views.home
  (:require [archetype.util :refer [>evt <sub]]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.views.splash :as splash]))

(defn- handle-input [k]
  (case k
    "r" (>evt [:providers/refresh!])

    nil))

(defn- initialized []
  (use-input handle-input)

  [frame
   [header "Gakki Home"]

   [:f> carousels]])

(defn view []
  (if (<sub [:initializing?])
    [splash/view]
    [:f> initialized]))
