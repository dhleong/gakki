(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   inject-cofx
                                   path trim-v]]
            [vimsical.re-frame.cofx.inject :as inject]
            [gakki.db :as db]
            [gakki.const :refer [max-volume-int]]
            [gakki.util.coll :refer [index-of]]
            [gakki.util.logging :as log]
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

(reg-event-db
  :navigate/back!
  [trim-v]
  (fn [db _]
    ; TODO backstack
    (assoc db :page [:home])))


; ======= Auth/Providers ==================================

(reg-event-fx
  :auth/set
  [trim-v]
  (fn [{:keys [db]} [accounts]]
    {:db (assoc db :accounts accounts)
     :providers/load! accounts}))

(reg-event-fx
  :auth/save
  [trim-v]
  (fn [{:keys [db]} [provider account]]
    {:db (assoc-in db [:accounts provider] account)
     :providers/load! {provider account}
     :auth/save! [provider account]}))

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


; ======= Carousel shared =================================

(def ^:private carousel-path
  {:id :carousel-path
   :before (fn [{{db :db} :coeffects :as context}]
             (let [page (:page db)]
               (assoc-in context [:coeffects :carousel]
                         (get-in db (cons :gakki.subs/carousel-data page)))))
   :after (fn [{{result :carousel new-db :db} :effects
                {:keys [db]} :coeffects
                :as context}]
            (let [page (:page db)]
              (if result
                (-> context
                    ; Ensure there is *some* db passed through:
                    (assoc-in [:effects :db] (or new-db db))

                    ; Apply the carousel fx into the correct path:
                    (assoc-in (concat [:effects :db :gakki.subs/carousel-data]
                                      page)
                              result)

                    ; Finally, remove the (handled here) carousel effect
                    (update :effects dissoc :carousel))
                context)))})

(reg-event-fx
  :carousel/navigate-categories
  [trim-v carousel-path
   (inject-cofx ::inject/sub [:carousel/categories])]
  (fn [{categories :carousel/categories} [direction]]
    (let [delta (case direction
                  :up -1
                  :down 1)
          idx (index-of categories :selected?)]
      (when-let [next-category (when idx
                                 (nth categories
                                      (mod (+ idx delta)
                                           (count categories))))]
        {:carousel {:selection {:category (category-id next-category)}
                    :selected (first (:items next-category))}}))))

(reg-event-fx
  :carousel/navigate-row
  [trim-v carousel-path
   (inject-cofx ::inject/sub [:carousel/selection])
   (inject-cofx ::inject/sub [:carousel/categories])]
  (fn [{categories :carousel/categories selections :carousel/selection}
       [direction]]
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
        {:carousel {:selection {:category (category-id category)
                                :item (:id next-item)}
                    :selected next-item}}))))

(reg-event-fx
  :carousel/open-selected
  [trim-v carousel-path (inject-cofx ::inject/sub [:page])]
  (fn [{{:keys [selected]} :carousel} _]
    (when selected
      {:dispatch [:player/open selected]})))


; ======= Player control ==================================

(defn- inflate-item [db {:keys [id kind] :as item}]
  (or (get-in db [kind id])

      item))

(reg-event-fx
  :player/open
  [trim-v]
  (fn [{:keys [db]} [item]]
    (let [item (inflate-item db item)]
      (case (:kind item)
        :song {:dispatch [::set-current-playable item]}

        :playlist (if-let [items (seq (:items item))]
                    {:dispatch [:player/play-items items]}

                    ; Unresolved playlist; fetch and resolve now:
                    {:providers/resolve-and-open [:playlist (:accounts db) item]})

        :album (if (:items item)
                 {:dispatch [:navigate! [:album (:id item)]]}

                 ; Unresolved album; fetch and resolve now:
                 {:providers/resolve-and-open [:album (:accounts db) item]})

        :artist (if (:categories item)
                  {:dispatch [:navigate! [:artist (:id item)]]}

                  {:providers/resolve-and-open [:artist (:accounts db) item]})

        (println "TODO support opening: " item)))))

(reg-event-fx
  :player/on-resolved
  [trim-v]
  (fn [{:keys [db]} [entity-kind entity ?action]]
    {:db (assoc-in db [entity-kind (:id entity)] entity)
     :fx [(when (= :action/open ?action)
            [:dispatch [:player/open entity]])]}))

(reg-event-fx
  :player/play-items
  [trim-v (path :player :queue)]
  (fn [_ [items ?selected-index]]
    {:db (if (nil? ?selected-index)
           items
           (vec (concat
                  (drop ?selected-index items)
                  (take ?selected-index items))))
     :dispatch [::set-current-playable (if (nil? ?selected-index)
                                         (first items)
                                         (nth items ?selected-index))]}))

(reg-event-fx
  ::set-current-playable
  [trim-v (inject-cofx ::inject/sub [:player/volume-percent])]
  (fn [{:keys [db] volume-percent :player/volume-percent} [playable]]
    (println "set playable <- " playable)
    {:db (-> db
             (assoc-in [:player :current] playable)
             (assoc-in [:player :state] :playing))
     :native/set-now-playing! playable
     :player/play! {:item playable
                    :config {:volume-percent volume-percent}}}))

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
  :player/next-in-queue
  [trim-v (path :player)]
  (fn [{player-state :db} _]
    (if-let [next-item (first (next (:queue player-state)))]
      {:db (update player-state :queue next)
       :dispatch [::set-current-playable next-item]}

      ; nothing more in the queue
      {:db (assoc player-state :state :paused)
       :native/set-state! :paused})))

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

(defmulti ^:private handle-player-event (fn [_ {what :type}] what))

(defmethod handle-player-event :playable-end [_ _]
  (log/debug "playable end")
  {:dispatch [:player/next-in-queue]})

(defmethod handle-player-event :playable-ending [player-state _]
  (log/debug "playable ending")
  (when-let [next-item (first (next (:queue player-state)))]
    (log/debug "... prepare " next-item)
    {:player/prepare! next-item}))

(defmethod handle-player-event :default [_ {what :type}]
  (println "WARN: Unexpected player event type: " what))

(reg-event-fx
  :player/event
  [trim-v (path :player)]
  (fn [{:keys [db]} [event]]
    (println "player event: " event)
    (handle-player-event db event)))
