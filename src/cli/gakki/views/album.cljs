(ns gakki.views.album
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(defn track-row [track]
  [:> k/Text (:title track)])

(defn view [album-id]
  (k/useInput
    (fn [_input k]
      (when (j/get k :escape)
        (>evt [:navigate! [:home]]))))

  (let [album (<sub [:album album-id])]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [:> k/Box {:flex-direction :row
                :justify-content :space-between
                :padding-bottom 1}
      [:> k/Text {:color theme/text-color-disabled}
       "Albums / "]
      [:> k/Text {:color theme/header-color-on-background}
       (:title album)]
      [player-mini]]

     [:> k/Text {:color theme/text-color-on-background}
      (:description album)]

     [vertical-list
      :items (:items album)
      :follow-selected? true
      :render track-row]
     ]))
