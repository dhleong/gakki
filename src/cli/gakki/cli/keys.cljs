(ns gakki.cli.keys
  (:require [applied-science.js-interop :as j]))

(def ^:private keyword-dispatchables
  [:upArrow :downArrow :leftArrow :rightArrow
   :pageDown :pageUp :return :escape :tab :backspace :delete])

(def ^:private keyword-renames
  {:upArrow :up
   :downArrow :down
   :leftArrow :left
   :rightArrow :right
   :pageUp :page-up
   :pageDown :page-down})

(defn ->key [input k]
  (let [kw (some #(when (j/get k %) %) keyword-dispatchables)
        kw (get keyword-renames kw kw)]
    (or (when (and kw (j/get k :shift))
          (keyword "shift" (name kw)))

        ; NOTE: :tab, for example, seems to always have :ctrl as "true"
        ;  so we only bother with "shift" for now...
        kw

        ; For normal keys, since letters, for example, will have both shift and
        ; the capitalized letter, we only bother with ctrl for now...
        (when (j/get k :ctrl)
          (keyword "ctrl" input))

        input)))
