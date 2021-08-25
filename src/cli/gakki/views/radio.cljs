(ns gakki.views.radio
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.header :refer [header]]
            [gakki.theme :as theme]
            [gakki.views.queue :refer [track-list]]))

(defn radio-header [radio]
  [header {:padding-bottom 1}
   [:> k/Text {:color theme/text-color-disabled}
    "Radio / "]
   (:title radio)])

(defn view [id]
  (use-input {:help {:header "Radio"}})
  (let [radio (<sub [:radio id])]
    [:f> track-list
     :items (<sub [:radio/items-with-state id])
     :header [radio-header radio]
     :on-whole-list-selected #(>evt [:player/play-items radio])
     :on-index-selected #(>evt [:player/play-items radio %])]))


