(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   inject-cofx
                                   path trim-v]]
            [vimsical.re-frame.cofx.inject :as inject]
            [gakki.db :as db]
            [gakki.const :as const :refer [max-volume-int]]
            [gakki.util.coll :refer [index-of nth-or-nil]]
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
    (if (not= (:page db) new-page)
      (-> db
          (assoc :page new-page)
          (update :backstack conj (:page db)))
      db)))

(reg-event-db
  :navigate/replace!
  [trim-v]
  (fn [db [new-top-page]]
    (-> db
        (assoc :page new-top-page)
        (assoc :backstack []))))

(reg-event-db
  :navigate/back!
  [trim-v]
  (fn [db _]
    (if-let [prev (peek (:backstack db))]
      (-> db
          (assoc :page prev)
          (update :backstack pop))
      (assoc db :page [:home]))))


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
  (fn [{:keys [db]} [provider account {:keys [load-home?]
                                       :or {load-home? true}}]]
    {:db (assoc-in db [:accounts provider] account)
     :providers/load! (when load-home?
                        {provider account})
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
        :track {:dispatch [::set-current-playable item]}

        :playlist (if (seq (:items item))
                    {:dispatch [:navigate! [:playlist (:id item)]]}

                    ; Unresolved playlist; fetch and resolve now:
                    {:providers/resolve-and-open [:playlist (:accounts db) item]})

        :album (if (seq (:items item))
                 {:dispatch [:navigate! [:album (:id item)]]}

                 ; Unresolved album; fetch and resolve now:
                 {:providers/resolve-and-open [:album (:accounts db) item]})

        :artist (if (:categories item)
                  {:dispatch [:navigate! [:artist (:id item)]]}

                  {:providers/resolve-and-open [:artist (:accounts db) item]})

        (println "TODO support opening: " item)))))

(defn- clean-entity [entity]
  (if (and (seq (:items entity))
           (not (vector? (:items entity))))
    (update entity :items vec)
    entity))

(reg-event-fx
  :player/on-resolved
  [trim-v]
  (fn [{:keys [db]} [entity-kind entity ?action]]
    {:db (assoc-in db [entity-kind (:id entity)] (clean-entity entity))
     :fx [(when (= :action/open ?action)
            [:dispatch [:player/open entity]])]}))

(reg-event-fx
  :player/play-items
  [trim-v (path :player :queue)]
  (fn [_ [items ?selected-index]]
    (let [items (if (vector? items)
                  items
                  (vec items))]
      {:db {:items items
            :index (or ?selected-index 0)}
       :dispatch [::set-current-playable (if (nil? ?selected-index)
                                           (first items)
                                           (nth items ?selected-index))]})))

(reg-event-fx
  ::set-current-playable
  [trim-v (inject-cofx ::inject/sub [:player/volume-percent])]
  (fn [{:keys [db] volume-percent :player/volume-percent} [playable]]
    (println "set playable <- " playable "@" volume-percent)
    {:db (-> db
             (assoc-in [:player :current] playable)
             (assoc-in [:player :state] :playing))
     :integrations/set-state! {:item playable :state :playing}
     :native/set-now-playing! playable
     :player/play! {:item playable
                    :config {:volume-percent volume-percent}}}))

(reg-event-fx
  :player/play-pause
  [trim-v (path :player)]
  (fn [{{current-state :state :as player} :db} _]
    (when-let [new-state (case current-state
                           :playing :paused
                           :paused :playing
                           nil nil)]
      (assoc {:db (assoc player :state new-state)
              :integrations/set-state! {:state new-state
                                        :item (:current player)}
              :native/set-state! new-state}
             (case new-state
               :playing :player/unpause!
               :paused :player/pause!)
             :!))))

(reg-event-fx
  :player/next-in-queue
  [trim-v (path :player :queue)]
  (fn [{{current-index :index} :db} _]
    {:dispatch [:player/nth-in-queue (inc current-index)]}))

(reg-event-fx
  :player/rewind-or-prev-in-queue
  [trim-v (path :player :queue)]
  (fn [{{current-index :index} :db} _]
    ; TODO In theory, it might be nice for this to rewind if we are > N seconds
    ; into the playback of the track, but we don't keep have that information...
    ; ... yet. So for now, we just always go back in the queue.
    (when (> current-index 0)
      {:dispatch [:player/nth-in-queue (dec current-index)]})))

(reg-event-fx
  :player/nth-in-queue
  [trim-v (path :player)]
  (fn [{{{queue :items} :queue :as player-state} :db} [index]]
    (if-some [next-item (nth-or-nil queue index)]
      {:db (assoc-in player-state [:queue :index] index)
       :dispatch [::set-current-playable next-item]}

      ; nothing more in the queue
      {:db (assoc player-state :state :paused)
       :native/set-state! :paused})))

(reg-event-fx
  :player/set-volume
  [trim-v (path :player)]
  (fn [{player :db} [new-volume]]
    ; NOTE: new-volume should be in [0..max-volume-int]
    ; TODO persist volume preference
    (let [new-volume (-> new-volume
                         (max 0)
                         (min max-volume-int))]
      {:db (-> player
               (assoc :volume new-volume)
               (update :adjusting-volume? inc))
       :player/set-volume! (/ new-volume max-volume-int)
       :dispatch-later {:ms 1500 :dispatch [::stop-adjusting-volume]}})))

(reg-event-fx
  :player/volume-inc
  [trim-v (path :player)]
  (fn [{player :db} [delta]]
    (let [current-volume (or (:volume player)
                             max-volume-int)
          new-volume (+ current-volume delta)]
      {:dispatch [:player/set-volume new-volume]})))

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
  (let [queue (:queue player-state)
        next-index (inc (:index queue))]
    (when-let [next-item (nth-or-nil (:items queue) next-index)]
      (log/debug "... prepare " next-item)
      {:player/prepare! next-item})))

(defmethod handle-player-event :default [_ {what :type}]
  (println "WARN: Unexpected player event type: " what))

(reg-event-fx
  :player/event
  [trim-v (path :player)]
  (fn [{:keys [db]} [event]]
    (println "player event: " event)
    (handle-player-event db event)))


; ======= Integrations ====================================

(reg-event-fx
  :integrations/set-add
  [trim-v (path :integration-vars)]
  (fn [{:keys [db]} [set-name value]]
    (let [new-db (update db set-name (fnil conj #{}) value)]
      {:db new-db
       :dispatch (when-not (= db new-db)
                   [:integrations/update])})))

(reg-event-fx
  :integrations/set-remove
  [trim-v (path :integration-vars)]
  (fn [{:keys [db]} [set-name value]]
    (let [new-db (update db set-name (fnil disj #{}) value)]
      {:db new-db
       :fx [(when-not (= db new-db)
              [:dispatch [:integrations/update]])]})))

(reg-event-fx
  :integrations/update
  [trim-v]
  (fn [{{vars :integration-vars player :player} :db} _]
    {:fx [(let [base-perc (/ (:volume player) max-volume-int)]
            (if (seq (:voice-connected vars))
              [:player/set-volume! (* const/suppressed-volume-percent base-perc)]
              [:player/set-volume! base-perc]))]}))
