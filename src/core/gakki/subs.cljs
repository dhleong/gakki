(ns gakki.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)

(reg-sub
  :loading?
  (fn [db _]
    (> (:loading-count db) 0)))

(reg-sub
  :home/categories
  (fn [db _]
    ; TODO probably some sort of round-robin?
    (->> (:home/categories db)
         vals
         (apply concat))))
