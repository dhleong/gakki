(ns gakki.views.help
  (:require [archetype.util :refer [>evt]]
            ["ink" :as k]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(defn- keys-width-of [items]
  (transduce
    (map (comp count str first))
    max
    0
    items))

(defn- help-items [help]
  (let [keys-width (inc (keys-width-of help))]
    [:<>
     (for [[k desc] (sort-by (comp str first) help)]
       ^{:key k}
       [:> k/Box {:flex-direction :row}
        [:> k/Box {:width keys-width}
         [:> k/Text {:bold true} k]]
        [:> k/Text " "]
        [:> k/Text desc]])]))

(defn- help-section [{:keys [header] :as section}]
  [:<>
   [:> k/Box {:flex-direction :row}
    [:> k/Text {:underline true}
     header
     [:> k/Text {:color theme/text-color-disabled} " / Help"]]]
   [help-items (dissoc section :header)]
   [:> k/Newline]])

(defn view [help-sections]
  (use-input
    (fn [k]
      (case k
        :escape (>evt [:navigate/back!])
        nil)))

  [frame
   [vertical-list
    :items (reverse help-sections)  ; Last-registered is most relevant
    :render help-section
    :key-fn :header]])
