(ns gakki.events
  (:require [gakki.const :refer [max-volume-int 
]]
            [re-frame.core :refer [reg-event-db
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


; ======= Auth/Providers ==================================

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


; ======= Home control ====================================

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
      (when-let [next-category (when idx
                                 (nth categories
                                      (mod (+ idx delta)
                                           (count categories))))]
        {:db (assoc db
                    :home/selection {:category (category-id next-category)}
                    :home/selected (first (:items next-category)))}))))

(reg-event-fx
  :home/navigate-row
  [trim-v
   (inject-cofx ::inject/sub [:home/selection])
   (inject-cofx ::inject/sub [:home/categories])]
  (fn [{db :db categories :home/categories selections :home/selection} [direction]]
    (let [{:keys [items] :as category} (or (when-let [id (:category selections)]
                                             (->> categories
                                                  (filter #(= id (category-id %)))
                                                  first))
                                           (first categories))
          delta (case direction
                  :left -1
                  :right 1)
          idx (or (index-of items :selected?) 0)]
      (when-let [next-item (when (seq items)
                             (nth items
                                  (mod (+ idx delta)
                                       (count items))))]
        {:db (assoc db
                    :home/selection {:category (category-id category)
                                     :item (:id next-item)}
                    :home/selected next-item)}))))

(reg-event-fx
  :home/open-selected
  [trim-v (inject-cofx ::inject/sub [:player/volume-percent])]
  (fn [{:keys [db] volume-percent :player/volume-percent} _]
    (when-let [item (:home/selected db)]
      (case (:kind item)
        :song {:db (-> db
                       (assoc-in [:player :current] item)
                       (assoc-in [:player :state] :playing))
               :native/set-now-playing! item
               :player/play! {:item item
                              :config {:volume-percent volume-percent}}}

        (println "TODO support opening: " item)))))


; ======= Player control ==================================

(reg-event-fx
  :player/play-pause
  [trim-v (path :player :state)]
  (fn [{current-state :db} _]
    (when-let [new-state (case current-state
                           :playing :paused
                           :paused :playing
                           nil nil)]
      (assoc {:db new-state
              :native/set-state! new-state}
             (case new-state
               :playing :player/unpause!
               :paused :player/pause!)
             :!))))

(reg-event-fx
  :player/volume-inc
  [trim-v (path :player)]
  (fn [{player :db} [delta]]
    ; TODO persist volume preference
    (let [current-volume (or (:volume player)
                             max-volume-int)
          new-volume (-> (+ current-volume delta)
                         (max 0)
                         (min max-volume-int))]
      {:db (-> player
               (assoc :volume new-volume)
               (update :adjusting-volume? inc))
       :player/set-volume! (/ new-volume max-volume-int)
       :dispatch-later {:ms 1500 :dispatch [::stop-adjusting-volume]}
       })))

(reg-event-db
  ::stop-adjusting-volume
  [trim-v (path :player :adjusting-volume?)]
  (fn [adjust-volume-count _]
    (dec adjust-volume-count)))


; ======= Player feedback =================================

(reg-event-fx
  :player/event
  [trim-v (path :player)]
  (fn [{:keys [db]} [{what :type}]]
    (case what
      :playable-end {:db (assoc db :state :paused)
                     :native/set-state! :paused}

      (println "WARN: Unexpected player event type: " what))))
