(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   ;; inject-cofx
                                   trim-v]]
            [gakki.db :as db]))

(reg-event-fx
  ::initialize-db
  (fn [_ _]
    {:db db/default-db
     :auth/load! :!}))

(reg-event-db
  :navigate!
  [trim-v]
  (fn [db [new-page]]
    (assoc db :page new-page)))

(reg-event-db
  :auth/set
  [trim-v]
  (fn [db [accounts]]
    (assoc db :accounts accounts)))
