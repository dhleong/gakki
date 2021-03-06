(ns gakki.views.playlist
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.header :refer [header]]
            [gakki.theme :as theme]
            [gakki.views.queue :refer [track-list]]))

(defn playlist-header [playlist]
  [header {:padding-bottom 1}
   [:> k/Text {:color theme/text-color-disabled}
    "Playlist / "]
   (:title playlist)])

(defn view [id]
  (use-input {:help {:header "Playlist"}})
  (let [playlist (<sub [:playlist id])]
    [:f> track-list
     :items (<sub [:playlist/items-with-state id])
     :header [playlist-header playlist]
     :on-whole-list-selected #(>evt [:player/play-items playlist])
     :on-index-selected #(>evt [:player/play-items playlist %])]))
