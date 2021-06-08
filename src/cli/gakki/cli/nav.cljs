(ns gakki.cli.nav
  (:require [archetype.util :refer [>evt]]
            [gakki.cli.input :refer [use-input]]))

(defn global-nav []
  (use-input
    (fn [k]
      (case k
        "/" (>evt [:navigate! [:search]])

        nil)))

  ; Nothing rendered here:
  nil)
