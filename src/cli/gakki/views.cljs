(ns gakki.views
  (:require ["ink" :as k]
            [archetype.util :refer [<sub]]))

(defn main []
  (let [accounts (<sub [:accounts])]
    [:> k/Box {:flex-direction :column
               :border-style :round}
     [:> k/Text {:color "red"}
      "Accounts="
      (or (when accounts
            (str accounts))
          "(none)")]
     [:> k/Text {:color "blue"} "Hello World!"]]))
