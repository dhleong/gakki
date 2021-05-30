(ns gakki.components.limited-text
  (:require ["ink" :as k]
            [reagent.core :as r]))

(defn- measure-element [element]
  (cond
    (string? element) (count element)
    (number? element) (count (str element))
    (vector? element) (when (= k/Text (second element))
                        (let [children (if (map? (nth element 2))
                                         (drop 3 element)
                                         (drop 2 element))]
                          (measure-element children)))
    (seq? element) (transduce
                     (map measure-element)
                     + element)
    :else 0))

(defn- recompute [children]
  {:children children
   :measured-width (measure-element children)})

(defn- maybe-recompute [old-state children]
  (if (= (:children old-state) children)
    old-state
    (recompute children)))

(defn limited-text [{:keys [max-width] :as opts} & children]
  (r/with-let [state (r/atom nil)]
    (swap! state maybe-recompute children)

    (let [measured-width (:measured-width @state)
          opts (update opts :wrap (fn [provided]
                                    (if (nil? provided)
                                      :truncate-end
                                      provided)))]
      [:> k/Box {:width (when measured-width
                          (min max-width measured-width))
                 :height 1}
       (into [:> k/Text opts] children)])))
