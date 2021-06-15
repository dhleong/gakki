(ns gakki.cli.nav
  (:require [archetype.util :refer [>evt]]
            [gakki.cli.input :refer [use-input]]))

(def ^:private help
  {:header "Global Navigation"
   "/" "Open Search"
   :escape "Go back"})

(defn global-nav []
  (use-input
    {"/" #(>evt [:navigate! [:search]])
     :escape #(>evt [:navigate/back!])
     :help help})

  ; Nothing rendered here:
  nil)
