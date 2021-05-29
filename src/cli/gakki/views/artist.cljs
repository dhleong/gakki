(ns gakki.views.artist
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.components.header :refer [header]]
            [gakki.theme :as theme]))

(defn view [artist-id]
  (let [artist (<sub [:artist artist-id])]
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
       "Artists / "]
      (:title artist)]

     [:f> carousels
      :categories (:categories artist)
      :navigate-categories! identity ; TODO
      :navigate-row! identity ; TODO
      :open-selected! identity]])
  )
