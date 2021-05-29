(ns gakki.views.queue
  (:require [archetype.util :refer [>evt <sub]]
            ["figures" :as figures]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.cli.subs :as subs]
            [gakki.components.header :refer [header]]
            [gakki.components.limited-text :refer [limited-text]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(def ^:private preferred-max-queue-height 20)

(defn queue-item [{:keys [selected?] :as track}]
  [:> k/Box {:flex-direction :row}
   [:> k/Text (if selected?
                figures/pointer
                " ")
    " "]

   [limited-text {:color theme/text-color-on-background
                  :wrap :truncate-end}
    (:title track)]
   [:> k/Text {:color theme/text-color-disabled} " / "]
   [limited-text {:wrap :truncate-end}
    (:artist track)]
   [:> k/Text {:color theme/text-color-disabled}
    " " figures/pointerSmall " "]
   [limited-text {:color theme/text-color-disabled
                  :wrap :truncate-end}
    (:album track)]])

(defn- queue-header [queue]
  (let [duration (<sub [:queue/duration-display])]
    [header {:padding-bottom 1}
    [:> k/Text {:color theme/text-color-disabled}
     "Queue / "]

    (count queue)
    " ♫"

    [:> k/Text {:color theme/text-color-disabled}
     "  / "
     duration]]))

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
                            available-height))]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [queue-header queue]

     [vertical-list
      :items queue
      :height rendered-height
      :per-page (or rendered-height 5)
      :render queue-item
      ]]))
