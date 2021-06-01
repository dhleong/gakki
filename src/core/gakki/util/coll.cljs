(ns gakki.util.coll)

(defn index-of [coll f]
  (loop [i 0
         coll coll]
    (cond
      (empty? coll) nil
      (f (first coll)) i

      :else (recur (inc i)
                (next coll)))))

(defn nth-or-nil
  "Many collection accessors return nil if the item doesn't exist (eg `first`
   or `second`) but `nth` will throw an exception. This is a simple utility
   wrapper to return `nil` if the provided `n` is an index beyond the length
   of the collection. It is expected that this will be used with a vector."
  [coll n]
  (when (< n (count coll))
    (nth coll n)))
