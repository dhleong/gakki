(ns gakki.persistent
  (:require [gakki.const :as const]))

(def spec
  [[:volume {:get (fn [db] [(get-in db [:player :volume])
                            const/max-volume-int])
             :set (fn [db [v _]]
                    (assoc-in db [:player :volume] v))}]])

(defn pull-state [db]
  (reduce
    (fn [state [k {get-value :get}]]
      (assoc state k (get-value db)))
    {}
    spec))

(defn restore-state [db state]
  (reduce
    (fn [db [k {set-value :set}]]
      (set-value db (get state k)))
    db
    spec))
