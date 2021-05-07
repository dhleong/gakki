(ns gakki.views.auth
  (:require [archetype.util :refer [<sub]]
            [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["ink" :as k]
            [gakki.accounts :as accounts]
            [gakki.accounts.core :as ap]
            [gakki.theme :as theme]))

(defn- provider-row [selected-key k provider]
  (let [accounts (<sub [:accounts])
        selected? (= k selected-key)]
    [:> k/Box
     (if selected?
       [:> k/Text {:color theme/accent-color} " -> "]
       [:> k/Text "    "])

     [:> k/Text {:color theme/header-color-on-background}
      (ap/get-name provider)
      ": "]

     (if-let [info (get accounts k)]
       [:> k/Text {:color theme/text-color-on-background} 
        (str info)]
       [:> k/Text {:color theme/text-color-disabled}
        (if selected?
          "(Press Enter to configure)"
          "(Not configured)")])
     ]))

(defn- rotate-provider [providers current delta]
  (let [idx (->> providers
                 (map-indexed vector)
                 (filter (fn [_i [k _]]
                           (= current k)))
                 ffirst)
        next-idx (mod (+ idx delta) (count providers))]

    ; NOTE: providers is a list of [k, v]
    (first (nth providers next-idx))))

(defn- view-with-input [selected-atom providers]
  (let [selected-key @selected-atom
        rotate! (partial rotate-provider providers)]
    (k/useInput
      (fn [input k]
        (case input
          ; Switch "selected" account:
          "j" (swap! selected-atom rotate! 1)
          "k" (swap! selected-atom rotate! -1)

          (when (j/get k :return)
            (swap! selected-atom #(if (= % :ytm)
                                    [input k]
                                    :ytm))))
        ))

    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round}
     (when (empty? (<sub [:accounts]))
       [:> k/Text {:color theme/header-color-on-background}
        "Welcome to Gakki!"])

     [:> k/Text (str selected-key)]

     (for [[k provider] providers]
       ^{:key k}
       [provider-row selected-key k provider])]))

(defn view []
  (r/with-let [selected-atom (r/atom :ytm)
               providers (->> accounts/providers (sort-by first))]
    [:f> view-with-input selected-atom providers]))
