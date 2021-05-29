(ns gakki.components.carousels
  "Renders a sectional/carousel-based UI, like the Home page, or Artists"
  (:require ["figures" :as figures]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
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

(defn carousels [& {:keys [categories
                           navigate-categories!
                           navigate-row!
                           open-selected!]}]
  (use-input
    (fn [k]
      (case k
        "j" (navigate-categories! :down)
        "k" (navigate-categories! :up)
        "h" (navigate-row! :left)
        "l" (navigate-row! :right)

        :return (open-selected!)
        nil)))

  [vertical-list
   :items categories
   :follow-selected? true
   :key-fn :title
   :render category-row])
