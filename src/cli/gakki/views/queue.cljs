(ns gakki.views.queue
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.cli.subs :as subs]
            [gakki.components.header :refer [header]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(def ^:private preferred-max-queue-height 20)

(defn render-queue-item [track]
  [:> k/Text {:wrap :truncate-end}
   (str track)])

(defn view []
  (use-input
    (fn [k]
      (case k
        :escape (>evt [:navigate/back!])
        nil)))

  (let [queue (<sub [:player/queue])
        available-height (<sub [::subs/available-height])
        rendered-height (when available-height
                          (min
                            (count queue)
                            preferred-max-queue-height
                            available-height))
        duration (<sub [:queue/duration-display])]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [header {:padding-bottom 1}
      [:> k/Text {:color theme/text-color-disabled}
       "Queue / "]

      (count queue)
      " ♫"

      [:> k/Text {:color theme/text-color-disabled}
       "  / "
       duration]]

     [vertical-list
      :items queue
      :height rendered-height
      :per-page (or rendered-height 5)
      :render render-queue-item
      ]]))
