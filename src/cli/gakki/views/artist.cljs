(ns gakki.views.artist
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.carousels :refer [carousels]]
            [gakki.components.header :refer [header]]
            [gakki.components.frame :refer [frame]]
            [gakki.theme :as theme]))

(def ^:private help
  {"S" "Shuffle Artist"

   :header "Artist Page"})

(defn view [artist-id]
  (let [artist (<sub [:artist artist-id])]
    (use-input {"s" #(when-let [playlist (:shuffle artist)]
                       (>evt [:player/open playlist]))
                :help help})

    [frame
     [header
      [:> k/Text {:color theme/text-color-disabled}
       "Artists / "]
      (:title artist)]

     [:f> carousels]]))
