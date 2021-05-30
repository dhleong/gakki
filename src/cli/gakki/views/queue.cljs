(ns gakki.views.queue
  (:require [archetype.util :refer [>evt <sub]]
            ["figures" :as figures]
            ["ink" :as k]
            [reagent.core :as r]
            [gakki.cli.input :refer [use-input]]
            [gakki.cli.subs :as subs]
            [gakki.components.header :refer [header]]
            [gakki.components.limited-text :refer [limited-text]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]
            [gakki.util.functional :refer [length-wrapped]]))

(def ^:private preferred-max-queue-height 20)

(defn queue-item [{:keys [selected? current?] :as track}]
  [:> k/Box {:flex-direction :row}
   [:> k/Text {:color (if selected?
                        theme/header-color-on-background
                        theme/text-color-on-background)}
    (cond
      selected? figures/pointer
      current? "♫"
      :else " ")
    " "]

   [limited-text {:color theme/text-color-on-background
                  :italic current?
                  :underline selected?
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
  (r/with-let [selected-index (r/atom nil)]
    (let [queue (<sub [:queue/items-with-state])
          queue (if-let [selected-index @selected-index]
                  (update queue selected-index assoc :selected? true)
                  queue)
          available-height (<sub [::subs/available-height])
          rendered-height (when available-height
                            (min
                              (count queue)
                              preferred-max-queue-height
                              available-height))]

      (use-input
        (fn [k]
          (case k
            "j" (swap! selected-index (length-wrapped
                                        (fnil inc -1)
                                        (count queue)))
            "k" (swap! selected-index (length-wrapped
                                        (fnil dec 1)
                                        (count queue)))

            :escape (if (nil? @selected-index)
                      (>evt [:navigate/back!])
                      (reset! selected-index nil))

            :return (when-some [index @selected-index]
                      (>evt [:player/nth-in-queue index]))

            nil)))

      [:> k/Box {:flex-direction :column
                 :border-color theme/text-color-on-background
                 :border-style :round
                 :padding-x 1}
       [queue-header queue]

       [vertical-list
        :items queue
        :follow-selected? true
        :height rendered-height
        :per-page (or rendered-height 5)
        :render queue-item
        ]])))
