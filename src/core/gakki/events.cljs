(ns gakki.events
  (:require [re-frame.core :refer [;; reg-event-db
                                   reg-event-fx
                                   ;; inject-cofx
                                   ;; path trim-v
                                   ]]
            [gakki.db :as db]))

(reg-event-fx
  ::initialize-db
  (fn [_ _]
    {:db db/default-db
     ;; :auth/load! :! ; TODO
     }))
