(ns gakki.components.frame
  (:require ["ink" :as k]
            [gakki.theme :as theme]))

(defn frame [& children]
  (into [:> k/Box {:flex-direction :column
                   :border-color theme/text-color-on-background
                   :border-style :round
                   :padding-x 1}]
        children))
