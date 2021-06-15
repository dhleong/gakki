(ns gakki.views.home
  (:require [archetype.util :refer [>evt <sub]]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.views.splash :as splash]))

(def ^:private help
  {"r" "Reload home screen items"})

(defn- initialized []
  (use-input
    {"r" #(>evt [:providers/refresh!])
     :help (assoc help :header "Gakki Home")})

  [frame
   [header "Gakki Home"]

   [:f> carousels]])

(defn view []
  (if (<sub [:initializing?])
    [splash/view]
    [:f> initialized]))
