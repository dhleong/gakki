(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   inject-cofx
                                   path trim-v]]
            [vimsical.re-frame.cofx.inject :as inject]
            [gakki.db :as db]
            [gakki.util.coll :refer [index-of]]
            [gakki.util.media :refer [category-id]]))

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

(reg-event-db
  :home/replace
  [trim-v (path :home/categories)]
  (fn [db [provider-id {:keys [categories]}]]
    (assoc db provider-id categories)))

(reg-event-fx
  :home/navigate-categories
  [trim-v (inject-cofx ::inject/sub [:home/categories])]
  (fn [{db :db categories :home/categories} [direction]]
    (let [delta (case direction
                  :up -1
                  :down 1)
          idx (index-of categories :selected?)]
      (when-let [next-id (when idx
                           (category-id
                             (nth categories
                                  (mod (+ idx delta)
                                       (count categories)))))]
        {:db (assoc db :home/selection {:category next-id})}))))

(reg-event-fx
  :home/navigate-row
  [trim-v
   (inject-cofx ::inject/sub [:home/selection])
   (inject-cofx ::inject/sub [:home/categories])]
  (fn [{db :db categories :home/categories selections :home/selection} [direction]]
    (let [{:keys [items]} (or (when-let [id (:category selections)]
                                (->> categories
                                     (filter #(= id (category-id %)))
                                     first))
                              (first categories))
          delta (case direction
                  :left -1
                  :right 1)
          idx (index-of items :selected?)]
      (when-let [next-id (when idx
                           (:id (nth items
                                     (mod (+ idx delta)
                                          (count items)))))]
        {:db (assoc-in db [:home/selection :item] next-id)}))))
