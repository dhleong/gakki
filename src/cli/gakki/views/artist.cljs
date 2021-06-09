(ns gakki.views.artist
  (:require [archetype.util :refer [<sub]]
            ["ink" :as k]
            [gakki.components.carousels :refer [carousels]]
            [gakki.components.header :refer [header]]
            [gakki.components.frame :refer [frame]]
            [gakki.theme :as theme]))

(defn view [artist-id]
  (let [artist (<sub [:artist artist-id])]
    [frame
     [header
      [:> k/Text {:color theme/text-color-disabled}
       "Artists / "]
      (:title artist)]

     [:f> carousels]]))
