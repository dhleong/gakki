(ns gakki.views.auth
  (:require [archetype.util :refer [<sub >evt]]
            [reagent.core :as r]
            ["figures" :as figures]
            ["ink" :as k]
            [gakki.accounts :as accounts]
            [gakki.accounts.core :as ap]
            [gakki.cli.input :refer [use-input]]
            [gakki.theme :as theme]))

(defn- provider-row [selected-key k provider]
  (let [accounts (<sub [:accounts])
        selected? (= k selected-key)]
    [:> k/Box
     (if selected?
       [:> k/Text {:color theme/accent-color}
        " " figures/pointer " "]
       [:> k/Text "   "])

     [:> k/Text {:color theme/header-color-on-background}
      (ap/get-name provider)
      " "]

     (if-let [info (get accounts k)]
       [:> k/Text {:color theme/positive-color}
        (ap/describe-account provider info)
        " "
        figures/tick]

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
        rotate! (partial rotate-provider providers)
        accounts (<sub [:accounts])]
    (use-input
      (fn [k]
        (case k
          ; Switch "selected" account:
          "j" (swap! selected-atom rotate! 1)
          "k" (swap! selected-atom rotate! -1)

          :escape (>evt [:navigate/replace! [:home]])
          :return (>evt [:navigate/replace! [(keyword "auth" selected-key)]])

          nil)))

    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     [:> k/Text {:color theme/header-color-on-background}
      (if (empty? accounts)
        "Welcome to Gakki!"
        "Gakki Auth Config")]

     (when (empty? accounts)
       [:> k/Text {:color theme/text-color-on-background}
        "You will need to configure one or more services to continue"])

     [:> k/Text " "]

     (for [[k provider] providers]
       ^{:key k}
       [provider-row selected-key k provider])

     (when (seq accounts)
       [:<>
        [:> k/Text " "]
        [:> k/Text {:color theme/text-color-disabled}
         "Press "
         [:> k/Text {:color theme/text-color-on-background} "<esc>"]
         " to return"]])
     ]))

(defn view []
  (r/with-let [selected-atom (r/atom :ytm)
               providers (->> accounts/providers (sort-by first))]
    [:f> view-with-input selected-atom providers]))
