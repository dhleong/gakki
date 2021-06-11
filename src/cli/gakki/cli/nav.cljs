(ns gakki.cli.nav
  (:require [archetype.util :refer [>evt]]
            [gakki.cli.input :refer [use-input]]))

(defn global-nav []
  (use-input
    {"/" #(>evt [:navigate! [:search]])
     :escape #(>evt [:navigate/back!])})

  ; Nothing rendered here:
  nil)
