(ns gakki.views.album
  (:require [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(defn track-row [track]
  [:> k/Text (:title track)])

(defn view [album-id]
  (let [album (<sub [:album album-id])]
    (use-input
      (fn [k]
        (case k
          "P" (>evt [:player/play-items (:items album)])
          :escape (>evt [:navigate! [:home]])
          nil)))

    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [:> k/Box {:flex-direction :row
                :justify-content :space-between
                :padding-bottom 1}
      [:> k/Box {:flex-direction :row}
       [:> k/Text {:color theme/text-color-disabled}
        "Albums / "]

       [:> k/Text {:color theme/header-color-on-background}
        (:title album)]

       [:> k/Text {:color theme/text-color-disabled} " / "]

       [:> k/Text {:color theme/text-color-on-background}
        (:artist album)]]

      [player-mini]]

     [:> k/Text {:color theme/text-color-on-background}
      (:description album)]

     [vertical-list
      :items (:items album)
      :follow-selected? true
      :render track-row]
     ]))