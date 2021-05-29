(ns gakki.views.album
  (:require [archetype.util :refer [<sub >evt]]
            ["figures" :as figures]
            ["ink" :as k]
            [reagent.core :as r]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.header :refer [header]]
            [gakki.components.limited-text :refer [limited-text]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.theme :as theme]))

(def max-description-length 280)
(def max-album-title-length 20)
(def max-artist-name-length 20)

(defn- length-wrapped [f length]
  (fn wrapped [v]
    (mod (f v) length)))

(defn track-row [{:keys [title selected?]}]
  [:> k/Box {:flex-direction :row}
   (if selected?
     (when selected?
       [:> k/Text
        {:color theme/text-color-on-background}
        figures/pointer])
     [:> k/Text " "])

   [:> k/Text (when selected?
                {:color theme/text-color-on-background})
    " " title]])

(defn- album-header [album]
  [header
   [:> k/Box {:flex-direction :row
              :padding-bottom 1}
    [:> k/Text {:color theme/text-color-disabled}
     "Albums / "]

    [limited-text {:color theme/header-color-on-background
                   :max-width max-album-title-length
                   :underline true}
     (:title album)]

    [:> k/Text {:color theme/text-color-disabled} " / "]

    [limited-text {:color theme/text-color-on-background
                   :max-width max-artist-name-length}
     (:artist album)]]])

(defn- description [album]
  (when-not (empty? (:description album))
    [:<>
     [:> k/Text {:color theme/text-color-on-background}
      (let [desc (:description album)]
        (if (<= (count desc) max-description-length)
          desc
          (str (subs desc 0 (dec max-description-length))
               figures/ellipsis)))]
     [:> k/Text " "]]))

(defn view [album-id]
  (r/with-let [state (r/atom nil)]
    (let [album (<sub [:album album-id])
          items (:items album)
          items (if-let [selected-index (:selected-index @state)]
                  (assoc-in items [selected-index :selected?] true)
                  items)]
      (use-input
        (fn [k]
          (case k
            "j" (swap! state update :selected-index (length-wrapped
                                                      (fnil inc -1)
                                                      (count items)))
            "k" (swap! state update :selected-index (length-wrapped
                                                      (fnil dec 1)
                                                      (count items)))

            :return (if-let [index (:selected-index @state)]
                      (>evt [:player/play-items (:items album)
                             index])
                      (>evt [:player/play-items (:items album)]))
            :escape (if @state
                      (reset! state nil)
                      (>evt [:navigate/back!]))
            nil)))

      [:> k/Box {:flex-direction :column
                 :border-color theme/text-color-on-background
                 :border-style :round
                 :padding-x 1}
       [album-header album]

       [description album]

       [vertical-list
        :items items
        :follow-selected? true
        :per-page 10
        :render track-row]
       ])))
