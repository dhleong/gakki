(ns gakki.cli.input
  (:require [applied-science.js-interop :as j]
            [ink :as k]))

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

(defn use-input [f]
  (k/useInput
    (fn input-dispatcher [input k]
      (let [kw (some #(when (j/get k %) %) keyword-dispatchables)
            kw (get keyword-renames kw kw)]
        (f (or kw input))))))
