(ns gakki.views.artist
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.components.header :refer [header]]
            [gakki.components.frame :refer [frame]]
            [gakki.theme :as theme]))

(defn view [artist-id]
  (let [artist (<sub [:artist artist-id])]
    (use-input
      (fn [k]
        (case k
          :escape (>evt [:navigate/back!])
          nil)))

    [frame
     [header
      [:> k/Text {:color theme/text-color-disabled}
       "Artists / "]
      (:title artist)]

     [:f> carousels]]))
