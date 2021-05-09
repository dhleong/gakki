(ns gakki.util.coll)

(defn index-of [coll f]
  (loop [i 0
         coll coll]
    (cond
      (empty? coll) nil
      (f (first coll)) i

      :else (recur (inc i)
                (next coll)))))
