(ns gakki.events
  (:require [re-frame.core :refer [reg-event-db
                                   reg-event-fx
                                   inject-cofx
                                   path trim-v]]
            [vimsical.re-frame.cofx.inject :as inject]
            [gakki.db :as db]
            [gakki.const :as const :refer [max-volume-int]]
            [gakki.persistent :as persistent]
            [gakki.util.coll :refer [index-of nth-or-nil]]
            [gakki.util.logging :as log]
            [gakki.util.media :refer [category-id]]))

(def ^:private inject-sub (partial inject-cofx ::inject/sub))

(reg-event-fx
  ::initialize-db
  (fn [_ _]
    {:db db/default-db
     :auth/load! :!
     :persistent/load! :!
     :prefs/load! :!}))

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


; ======= Prefs ===========================================

(reg-event-fx
  :prefs/set
  [trim-v (path :prefs)]
  (fn [_ [prefs]]
    {:db prefs
     :integrations/configure! (merge
                                db/default-integrations
                                (:integrations prefs))}))


; ======= Persistent state ================================

(reg-event-fx
  :persistent/load!
  [trim-v]
  (fn [_ _]
    {:persistent/load! :!}))

(reg-event-db
  :persistent/set
  [trim-v]
  (fn [db [state]]
    (persistent/restore-state db state)))

(reg-event-fx
  :persistent/save
  [trim-v]
  (fn [{:keys [db]} _]
    {:persistent/save (persistent/pull-state db)}))


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
   (inject-sub [:carousel/categories])]
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
   (inject-sub [:carousel/selection])
   (inject-sub [:carousel/categories])]
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
  [trim-v carousel-path (inject-sub [:page])]
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

        (log/error "TODO support opening: " item)))))

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
  :player/on-playback-config-resolved
  [trim-v (path :player :current)]
  (fn [{current :db} [id {duration-ms :duration}]]
    (let [duration-seconds (js/Math.ceil (/ duration-ms 1000))]
      (when (and (= id (:id current))
                 (not= duration-seconds (:duration current)))
        (log/player "Providing duration" duration-seconds
                    " (was " (:duration current) ") for track #" id)
        (let [with-duration (assoc current :duration duration-seconds)]
          {:db with-duration
           :native/set-now-playing! with-duration})))))

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
  [trim-v (inject-sub [:player/volume-percent])]
  (fn [{:keys [db] volume-percent :player/volume-percent} [playable]]
    ((log/of :events/set-current-playable) "set playable <- " playable "@" volume-percent)
    {:db (-> db
             (assoc-in [:player :current] playable)
             (assoc-in [:player :state] :playing))
     :integrations/set-state! {:item playable :state :playing}
     :native/set-now-playing! playable
     :player/play! {:item playable
                    :account (get-in db [:accounts (:provider playable)])
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
  :player/play
  [trim-v (path :player)]
  (fn [{{current-state :state} :db} _]
    (when (= :paused current-state)
      {:dispatch [:player/play-pause]})))

(reg-event-fx
  :player/pause
  [trim-v (path :player)]
  (fn [{{current-state :state} :db} _]
    (when (= :playing current-state)
      {:dispatch [:player/play-pause]})))

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
  :player/seek-by
  [trim-v]
  (fn [_ [relative-seconds]]
    {:player/seek-by! relative-seconds}))

(reg-event-fx
  :player/seek-to
  [trim-v]
  (fn [_ [timestamp-seconds]]
    {:player/seek-to! timestamp-seconds}))

(reg-event-fx
  :player/set-volume
  [trim-v
   (path :player)
   (inject-sub [:player/volume-suppress-amount])]
  (fn [{player :db suppress-amount :player/volume-suppress-amount} [new-volume]]
    ; NOTE: new-volume should be in [0..max-volume-int]
    (let [new-volume (-> new-volume
                         (max 0)
                         (min max-volume-int))]
      {:db (-> player
               (assoc :volume new-volume)
               (update :adjusting-volume? inc))
       :player/set-volume! (* (/ new-volume max-volume-int)
                              suppress-amount)
       :dispatch-later {:ms 1500 :dispatch [::stop-adjusting-volume]}
       :dispatch [:persistent/save]})))

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
  ((log/of :player/events) "playable end")
  {:dispatch [:player/next-in-queue]})

(defmethod handle-player-event :playable-ending [db _]
  ((log/of :player/events) "playable ending")
  (let [{player-state :player accounts :accounts} db
        queue (:queue player-state)
        next-index (inc (:index queue))]
    (when-let [next-item (nth-or-nil (:items queue) next-index)]
      ((log/of :player/events) "... prepare " next-item)
      {:player/prepare! {:item next-item
                         :account (get accounts (:provider next-item))}})))

(defmethod handle-player-event :default [_ {what :type}]
  (log/error "Unexpected player event type: " what))

(reg-event-fx
  :player/event
  [trim-v]
  (fn [{:keys [db]} [event]]
    (handle-player-event db event)))


; ======= Cache management ================================

(reg-event-fx
  :cache/download-completed
  [trim-v (inject-sub [:prefs :cache.size])]
  (fn [{cache-size :prefs} [path]]
    (println "TODO check cache vs " cache-size
             "downloaded: " path)))

(reg-event-fx
  :cache/file-accessed
  [trim-v]
  (fn [_ [path]]))


; ======= Integrations ====================================

(reg-event-fx
  :integrations/set-add
  [trim-v (path :integration-vars)]
  (fn [{:keys [db]} [set-name value]]
    (let [new-db (update db set-name (fnil conj #{}) value)]
      {:db new-db
       :fx [(when-not (= db new-db)
              [:dispatch [:integrations/update]])]})))

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
  [trim-v (inject-cofx ::inject/sub [:player/volume-percent])]
  (fn [{{vars :integration-vars} :db volume :player/volume-percent} _]
    {:fx [(when-not (nil? (:voice-connected vars))
            [:player/set-volume! volume])]}))


; ======= Search ==========================================

(reg-event-fx
  :search/reload!
  [trim-v]
  (fn [{:keys [db]} [query]]
    {:db (assoc-in db [:search query] :loading)
     :providers/search [(:accounts db) query]}))

(reg-event-db
  :search/on-loaded
  [trim-v (path :search)]
  (fn [{search :db} [query result]]
    (assoc search query result)))
