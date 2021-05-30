(ns gakki.components.carousels
  "Renders a sectional/carousel-based UI, like the Home page, or Artists.
   The data is automatically pulled from the `[:carousel/categories]` sub"
  (:require [archetype.util :refer [<sub >evt]]
            ["figures" :as figures]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.cli.subs :as subs]
            [gakki.components.scrollable :refer [horizontal-list
                                                 vertical-list]]
            [gakki.theme :as theme]))

(defn- category-item [{:keys [title selected?]}]
  [:> k/Box {:width :20%
             :padding-x 1}
   ; TODO Could we tint colors based on album art?
   [:> k/Text
    (when selected?
      figures/pointer)
    title]])

(defn- category-row [{:keys [title items selected?]}]
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
    :render category-item]])

(defn carousels []
  (use-input
    (fn [k]
      (case k
        "j" (>evt [:carousel/navigate-categories :down])
        "k" (>evt [:carousel/navigate-categories :up])
        "h" (>evt [:carousel/navigate-row :left])
        "l" (>evt [:carousel/navigate-row :right])

        :return (>evt [:carousel/open-selected])
        nil)))

  (let [available-height (<sub [::subs/available-height])]
    [vertical-list
     :items (<sub [:carousel/categories])
     :follow-selected? true
     :key-fn :title
     :per-page (js/Math.floor
                 (/ available-height 5))
     :render category-row]))
