(ns gakki.views.home
  (:require [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.components.scrollable :refer [horizontal-list
                                                 vertical-list]]
            [gakki.theme :as theme]))

(defn- handle-input [input _k]
  (case input
    "r" (>evt [:providers/refresh!])

    nil ; ignore, for now
    ))

(defn- category-item [{:keys [title]}]
  [:> k/Box {:width :20%
             :padding-x 1}
   [:> k/Text title]])

(defn- category-row [{:keys [title items]}]
  ; TODO Could we tint colors based on album art?
  [:> k/Box {:flex-direction :column
             :padding-top 1}
   [:> k/Text {:color theme/text-color-on-background} title
    [:> k/Text {:color theme/text-color-disabled}
     " (" (count items) ")"]]
   [horizontal-list
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
     [:> k/Text {:color theme/header-color-on-background}
      (if (<sub [:loading?])
        [:> Spinner {:type "dots"}]
        " ")
      " Gakki Home"]

     [vertical-list
      :items categories
      :key-fn :title
      :render category-row]
     ]))
