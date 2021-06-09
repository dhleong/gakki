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

(defn vec-dissoc [coll v]
  (cond
    ; Special optimizations for last...
    (= v (peek coll))
    (pop coll)

    ; ... and first
    (= v (first coll))
    (subvec coll 1)

    ; Expensive case
    :else
    (if-let [idx (index-of coll (partial = v))]
      (into (subvec coll 0 idx)
            (subvec coll (inc idx)))
      coll)))
