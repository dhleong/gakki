(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
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

(reg-event-fx
  :auth/set
  [trim-v]
  (fn [{:keys [db]} [accounts]]
    {:db (assoc db :accounts accounts)
     :providers/load! accounts}))

(reg-event-db
  :loading/update-count
  [trim-v]
  (fn [db [update-f]]
    (update db :loading-count update-f)))

(reg-event-fx
  :providers/refresh!
  [trim-v]
  (fn [{:keys [db]} _]
    (when-let [accounts (:accounts db)]
      {:providers/load! accounts})))
