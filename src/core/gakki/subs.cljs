(ns gakki.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)

(reg-sub
  :loading?
  (fn [db _]
    (> (:loading-count db) 0)))
