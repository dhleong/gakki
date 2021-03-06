(ns gakki.components.player-mini
  (:require [archetype.util :refer [<sub >evt]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.theme :as theme]))

(def ^:private help
  {"q" "Show the queue"
   :space "Play/Pause the current item"
   "[ ]" "Decrease / Increase volume"

   :header "Player UI"})

(defn- player-ui [playing volume playing?]
  (use-input
    {"q" #(>evt [:navigate! [:queue]])
     " " #(>evt [:player/play-pause])
     "[" #(>evt [:player/volume-inc -1])
     "]" #(>evt [:player/volume-inc 1])
     :help help})

  [:> k/Box {:flex-grow 1
             :flex-direction :row
             :justify-content :flex-end}

   (when-not (nil? volume)
     [:> k/Text {:color theme/text-color-on-background}
      "Volume: " (int (* volume 100)) "% "])

   (when playing?
     [:> k/Text {:color theme/text-color-on-background} "♫ "])

   [:> k/Text {:color (if playing?
                        theme/text-color-on-background
                        theme/text-color-disabled)}
    (:title playing)]])

(defn player-mini []
  (let [playing (<sub [:player/item])
        state (<sub [:player/state])
        volume (<sub [:player/adjusting-volume-percent])

        playing? (= :playing state)]
    (when playing
      [:f> player-ui playing volume playing?])))
