(ns gakki.views.help
  (:require ["ink" :as k]
            [gakki.components.frame :refer [frame]]
            [gakki.theme :as theme]))

(defn view [{:keys [header] :as help}]
  [frame
   [:> k/Box {:flex-direction :row}
    header
    [:> k/Text {:color theme/text-color-disabled} " / Help"]]
   [:> k/Newline]
   [:> k/Text (str (dissoc help :header))]
   ])
