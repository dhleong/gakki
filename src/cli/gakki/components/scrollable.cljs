(ns gakki.components.scrollable
  (:require ["ink" :as k]
            [reagent.core :as r]))

(defn scrollable-list [& {:keys [flex-direction
                                 key-fn
                                 items
                                 render]}]
  ; TODO support persisting scroll position via re-frame
  (r/with-let [scroll (r/atom 0)
               key-fn (or key-fn :id)]
    (let [items (->> items
                     (drop @scroll)
                     (take 5))]
      [:> k/Box {:flex-direction flex-direction}
       (for [item items]
         ^{:key (key-fn item)}
         [render item])])))

(defn vertical-list [& {:keys [key-fn items render]}]
  [scrollable-list
   :flex-direction :column
   :key-fn key-fn
   :items items
   :render render])

(defn horizontal-list [& {:keys [key-fn items render]}]
  [scrollable-list
   :flex-direction :row
   :key-fn key-fn
   :items items
   :render render])
