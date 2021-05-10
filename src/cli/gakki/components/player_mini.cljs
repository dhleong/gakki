(ns gakki.components.player-mini
  (:require [archetype.util :refer [<sub]]
            ["ink" :as k]
            [gakki.theme :as theme]))

(defn player-mini []
  (let [playing (<sub [:player/item])
        state (<sub [:player/state])
        volume (<sub [:player/adjusting-volume-percent])

        playing? (= :playing state)]
    (when playing
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
        (:title playing)]
       ])))
