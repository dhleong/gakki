(ns gakki.util.functional)

(defn length-wrapped
  "Given an update function `f` that expects a number and returns
   an updated number, and a limiting `length`, return a new function
   that applies the same update as `f` but wraps numbers with the
   range of `0..<length`"
  [f length]
  (fn wrapped [v]
    (mod (f v) length)))

