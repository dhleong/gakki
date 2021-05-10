(ns gakki.views.home
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [<sub >evt]]
            ["figures" :as figures]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.components.player-mini :refer [player-mini]]
            [gakki.components.scrollable :refer [horizontal-list
                                                 vertical-list]]
            [gakki.theme :as theme]))

(defn- handle-input [input k]
  (case input
    "r" (>evt [:providers/refresh!])

    "j" (>evt [:home/navigate-categories :down])
    "k" (>evt [:home/navigate-categories :up])
    "h" (>evt [:home/navigate-row :left])
    "l" (>evt [:home/navigate-row :right])

    ; TODO: these should probably be global...
    "p" (>evt [:player/play-pause])
    "[" (>evt [:player/volume-inc -1])
    "]" (>evt [:player/volume-inc 1])

    (cond
      (j/get k :return) (>evt [:home/open-selected])

      :else (when goog.DEBUG
              (println input k)))))

(defn- category-item [{:keys [title selected?]}]
  [:> k/Box {:width :20%
             :padding-x 1}
   [:> k/Text
    (when selected?
      figures/pointer)
    title]])

(defn- category-row [{:keys [title items selected?]}]
  ; TODO Could we tint colors based on album art?
  [:> k/Box {:flex-direction :column
             :padding-top 1}
   [:> k/Text {:color theme/text-color-on-background}
    (when selected?
      figures/pointer)
    title
    [:> k/Text {:color theme/text-color-disabled}
     " (" (count items) ")"]]
   [horizontal-list
    :follow-selected? selected?
    :items items
    :render category-item]
   ])

(defn view []
  (k/useInput handle-input)

  (let [categories (<sub [:home/categories])]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [:> k/Box {:flex-direction :row
                :justify-content :space-between}
      [:> k/Text {:color theme/header-color-on-background}
       (if (<sub [:loading?])
         [:> Spinner {:type "dots"}]
         " ")
       " Gakki Home"]
      [player-mini]]

     [vertical-list
      :items categories
      :follow-selected? true
      :key-fn :title
      :render category-row]
     ]))
