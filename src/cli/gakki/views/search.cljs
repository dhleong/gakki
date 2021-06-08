(ns gakki.views.search
  (:require [archetype.util :refer [>evt]]
            ["ink-text-input" :default TextInput]
            ["react" :rename {useState use-state}]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]))

(defn view []
  ; NOTE: use-state seems *slightly* faster here than using a ratom for typing
  (let [[input set-input!] (use-state "")]
    (use-input
      (fn [k]
        (case k
          :escape (if (empty? input)
                    (>evt [:navigate/back!])
                    (set-input! ""))
          nil)))

    [frame
     [header "Search"]
     [:> TextInput {:value input :on-change set-input!}]]))
