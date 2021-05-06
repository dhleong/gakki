(ns gakki.views.auth
  (:require [archetype.util :refer [<sub]]
            [reagent.core :as r]
            ["ink" :as k]
            [gakki.accounts :as accounts]
            [gakki.accounts.core :as ap]
            [gakki.theme :as theme]))

(defn view [{:keys [forced?]}]
  (r/with-let [selected (r/atom :ytm)]
    (let [accounts (<sub [:accounts])
          selected-key @selected]
      [:> k/Box {:flex-direction :column
                 :border-color theme/text-color-on-background
                 :border-style :round}
       (when forced?
         [:> k/Text {:color theme/header-color-on-background}
          "Welcome to Gakki!"])

       (for [[k provider] (->> accounts/providers (sort-by first))]
         (let [selected? (= k selected-key)]
           [:> k/Box {:key k}
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
            ]))])))
