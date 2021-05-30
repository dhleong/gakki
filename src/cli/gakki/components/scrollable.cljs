(ns gakki.components.scrollable
  (:require ["ink" :as k]
            [reagent.core :as r]
            [gakki.util.coll :refer [index-of]]))

(defn scrollable-list [{:keys [flex-direction
                               follow-selected?
                               height
                               key-fn
                               per-page
                               items
                               render]
                        :or {per-page 5}}]
  (r/with-let [scroll (r/atom 0)
               last-selected (atom nil) ; NOT r/atom, to avoid re-render
               key-fn (or key-fn :id)]

    (let [scroll (or (when follow-selected?
                       (when-let [selected-idx (index-of items :selected?)]
                         (when-not (= @last-selected selected-idx)
                           (reset! last-selected selected-idx)
                           (* (js/Math.floor (/ selected-idx per-page))
                              per-page))))

                     @scroll)
          items (->> items
                     (drop scroll)
                     (take per-page))]
      [:> k/Box {:flex-direction flex-direction
                 :height height}
       (for [item items]
         ^{:key (key-fn item)}
         [render item])])))

(defn vertical-list [& {:as args}]
  [scrollable-list
   (assoc args :flex-direction :column)])

(defn horizontal-list [& {:as args}]
  [scrollable-list
   (assoc args :flex-direction :row)])
