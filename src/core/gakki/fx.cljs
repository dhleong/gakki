(ns gakki.fx
  (:require [archetype.util :refer [>evt]]
            [re-frame.core :refer [reg-fx]]
            [re-frame.registrar :refer [get-handler]]
            [promesa.core :as p]
            [gakki.accounts.core :as ap]
            [gakki.accounts :as accounts :refer [providers]]
            [gakki.integrations :as integrations]
            [gakki.native :as native]
            [gakki.player :as player]
            [gakki.player.cache :as cache]
            [gakki.util.loading :refer [with-loading-promise]]
            [gakki.util.logging :as log]))


; ======= Auth ============================================

(reg-fx
  :auth/load!
  (fn []
    (log/with-timing-promise :fx/auth-load!
      (p/let [accounts (native/load-accounts)]
        (>evt [:auth/set accounts])))))

(reg-fx
  :auth/save!
  (fn [[provider account]]
    (native/add-account provider account)))



; ======= Debouncing ======================================

(defonce ^:private dedup-promise-state (atom nil))

(reg-fx
  :dedup-promised-fx
  (fn [fx]
    (swap! dedup-promise-state
           (fn [state fx]
             (if (get state fx)
               state
               (assoc state fx
                      (let [[fx-name args] fx
                            handler (get-handler :fx fx-name :required!)]
                        (when-let [p (handler args)]
                          (-> p
                              (p/finally #(swap! dedup-promise-state dissoc fx))))))))
           fx)))


; ======= Prefs ===========================================

(reg-fx
  :prefs/load!
  (fn []
    (log/with-timing-promise :fx/prefs-load!
      (p/let [prefs (native/load-prefs)]
        (>evt [:prefs/set prefs])))))


; ======= Persistent state ================================

(defonce ^:private persistent-debounce-state (atom nil))
(def ^:private persistent-debounce-delay 2000)

(defn- persist-pending-state []
  (p/let [to-persist @persistent-debounce-state

          ; clear the persistent state *for now* so any concurrent writes
          ; will be enqueued
          _ (reset! persistent-debounce-state nil)

          state-to-write (:pending to-persist)]

    ; NOTE: this double when looks pointless, but it allows promesa
    ; to wait on the promise returned from save-persitent-state before
    ; performing the final swap!
    (when state-to-write
      (native/save-persistent-state state-to-write))

    (when state-to-write
      (log/debug "Persisted state.")
      (swap! persistent-debounce-state
             (fn [old-state]
               (if (= (:pending old-state) state-to-write)
                 (do (js/clearTimeout (:timeout old-state))
                     {:on-disk state-to-write})

                 (assoc old-state :on-disk state-to-write)))))))

(reg-fx
  :persistent/save
  (fn [state]
    (swap! persistent-debounce-state
           (fn [debounce-state]
             (cond
               ; This state is already on disk; cancel any pending writes
               (= (:on-disk debounce-state) state)
               (do (js/clearTimeout (:timeout debounce-state))
                   (dissoc debounce-state :timeout :pending))

               ; This state is already pending a write; ignore
               (= (:pending debounce-state) state)
               debounce-state

               :else
               (do
                 (js/clearTimeout (:timeout debounce-state))
                 {:on-disk (:on-disk debounce-state)
                  :pending state
                  :timeout (js/setTimeout
                             persist-pending-state
                             persistent-debounce-delay)}))))))

(reg-fx
  :persistent/load!
  (fn []
    (log/with-timing-promise :fx/persistent-load!
      (p/let [prefs (native/load-persistent-state)]
        (>evt [:persistent/set prefs])))))


; ======= Provider-based loading ==========================

(reg-fx
  :providers/load!
  (fn [accounts]
    (when (seq accounts)
      (->> accounts
           (map (fn [[k account]]
                  (when-let [provider (get providers k)]
                    (-> (p/let [results (ap/fetch-home provider account)]
                          (when results
                            (>evt [:home/replace k results])))
                        (p/catch (fn [e]
                                   (log/error "Loading home from " k ":" e)))))))
           p/all
           (with-loading-promise :providers/load!)))))

(reg-fx
  :providers/paginate!
  (fn [{:keys [accounts index] {k :provider :keys [kind] :as entity} :entity}]
    (let [provider (get providers k)
          account (get accounts k)]

      (if (and provider account)
        (when-let [p (ap/paginate provider account entity index)]
          (-> (p/let [{new-entity :entity next-items :next-items :as result} p]
                (if (seq next-items)
                  (do
                    ((log/of :providers/paginate!)
                     "Loaded " (count next-items) "of" (:id entity) "via pagination")
                    (>evt [:player/on-resolved kind new-entity :action/queue-entity])
                    (>evt [:player/enqueue-items next-items]))

                  (log/error "Empty " kind " from paginating " k " = " result)))

              (with-loading-promise :providers/paginate!)

              (p/catch (fn [e]
                         (log/error "Resolving " kind " from " e ": " e)))))

        (log/error "Invalid provider or no account: " k)))))

(reg-fx
  :providers/resolve-and-open
  (fn [[kind accounts entity]]
    ; NOTE: kind must be eg: :playlist, :album
    ; NOTE: entity must have :provider and :id keys
    (let [k (:provider entity)
          provider (get providers k)
          account (get accounts k)]

      (if (and provider account)
        (-> (p/let [f (case kind
                        :album ap/resolve-album
                        :artist ap/resolve-artist
                        :playlist ap/resolve-playlist
                        :radio ap/resolve-radio)
                    result (f provider
                              account
                              entity)]
              (if (or (seq (:items result))
                      (seq (:categories result)))
                (>evt [:player/on-resolved kind result :action/open])
                (log/error "Empty " kind " from " k " = " result)))

            (with-loading-promise :providers/resolve-and-open)

            (p/catch (fn [e]
                       (log/error "Resolving " kind " from " e ": " e))))

        (log/error "Invalid provider or no account: " k)))))

(reg-fx
  :providers/search
  (fn [[accounts query]]
    ; NOTE: kind must be eg: :playlist, :album
    ; NOTE: entity must have :provider and :id keys
    (-> (p/let [results (accounts/search accounts query)]
          (>evt [:search/on-loaded query results]))
        (p/catch (fn [e]
                   (log/error "Performing search:" e)
                   (>evt [:search/on-loaded query e]))))))

; ======= Player ==========================================

(reg-fx :player/pause! player/pause!)
(reg-fx :player/play! player/play!)
(reg-fx :player/prepare! player/prepare!)
(reg-fx :player/seek-by! player/seek-by!)
(reg-fx :player/seek-to! player/seek-to!)
(reg-fx :player/set-volume! player/set-volume!)
(reg-fx :player/unpause! player/unpause!)


; ======= Cache ===========================================

(defonce ^:private player-cache (atom nil))

(reg-fx
  :cache/file-accessed
  (fn [path]
    (when-let [cache @player-cache]
      (cache/on-file-accessed cache path))))

(reg-fx
  :cache/download-completed
  (fn [{:keys [cache-size path]}]
    (let [cache (swap! player-cache cache/ensure-sized cache-size)]
      (cache/on-file-created cache path))))


; ======= Integrations ====================================

(reg-fx
  :integrations/configure!
  integrations/configure!)

(reg-fx
  :integrations/set-state!
  integrations/set-state!)
