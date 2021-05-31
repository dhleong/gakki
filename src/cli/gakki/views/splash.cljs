(ns gakki.views.splash
  (:require ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [gakki.theme :as theme]))

(defn view []
  [:> k/Box {:flex-direction :row}
   [:> k/Text {:color theme/text-color-on-background}
    [:> Spinner {:type "dots"}]
    " Tuning the instruments..."]])
