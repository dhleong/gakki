(ns gakki.fx
  (:require [archetype.util :refer [>evt]]
            [re-frame.core :refer [reg-fx]]
            [promesa.core :as p]
            [gakki.accounts.core :as ap]
            [gakki.accounts :as accounts :refer [providers]]
            [gakki.integrations :as integrations]
            [gakki.native :as native]
            [gakki.player :as player]
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
      (>evt [:loading/update-count inc])
      (->> accounts
           (map (fn [[k account]]
                  (when-let [provider (get providers k)]
                    (-> (p/let [results (ap/fetch-home provider account)]
                          (when results
                            (>evt [:home/replace k results])))
                        (p/catch (fn [e]
                                   (log/error "Loading home from " k ":" e)))))))
           p/all
           (p/map (fn []
                    (>evt [:loading/update-count dec])))))))

(reg-fx
  :providers/resolve-and-open
  (fn [[kind accounts entity]]
    ; NOTE: kind must be eg: :playlist, :album
    ; NOTE: entity must have :provider and :id keys
    (let [k (:provider entity)
          provider (get providers k)
          account (get accounts k)]

      (if (and provider account)
        (-> (p/let [_ (>evt [:loading/update-count inc]) ; start by loading

                    f (case kind
                        :album ap/resolve-album
                        :artist ap/resolve-artist
                        :playlist ap/resolve-playlist)
                    result (f provider
                              account
                              (:id entity))]
              (if (or (seq (:items result))
                      (seq (:categories result)))
                (>evt [:player/on-resolved kind result :action/open])
                (log/error "Empty " kind " from " k " = " result)))

            (p/catch (fn [e]
                       (log/error "Resolving " kind " from " e ": " e)))

            (p/finally (fn []
                         (>evt [:loading/update-count dec]))))

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
(reg-fx :player/set-volume! player/set-volume!)
(reg-fx :player/unpause! player/unpause!)


; ======= Integrations ====================================

(reg-fx
  :integrations/configure!
  integrations/configure!)

(reg-fx
  :integrations/set-state!
  integrations/set-state!)
