(ns gakki.cli.dimen
  (:require [archetype.util :refer [>evt]]
            ["ink-use-stdout-dimensions" :as use-stdout-dimensions]
            [gakki.cli.events :as events]))

(defn dimens-tracker []
  (let [[width height] (use-stdout-dimensions)]
    (>evt [::events/set-dimens width height])))
