(ns gakki.cli.dimen
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            [gakki.cli.events :as events]
            ["ink" :as k] ; ["ink-use-stdout-dimensions" :as use-stdout-dimensions]
            ))

(defn dimens-tracker []
  (j/let [^:js {:keys [columns rows]} (k/useWindowSize)]
    (>evt [::events/set-dimens columns rows])))
