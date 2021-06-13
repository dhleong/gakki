(ns gakki.player
  (:require [archetype.util :refer [>evt]]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.core :as player]
            [gakki.util.logging :as log]))

(defonce ^:private state (atom nil))

(defn- listen-for-events [playable]
  (doto (player/events playable)
    (.once "ending" (fn on-ending []
                      (>evt [:player/event {:type :playable-ending}])))
    (.once "end" (fn on-end []
                   (>evt [:player/event {:type :playable-end}])))))

(defn- apply-config [playable config]
  (when-let [volume (:volume-percent config)]
    (player/set-volume playable volume)))

(defn- on-playable [f & args]
  (when-let [playable (:playable @state)]
    (apply f playable args)))

(defn- prepare-snapshot [snapshot {provider :provider :as item} account]
  (if-let [provider-obj (get providers provider)]
    (let [{existing-info :info} (:prepared snapshot)]
      (if (= existing-info item)
        ; Reuse the existing, prepared playable for this (it may be prefetching
        ; and or have started prefetching)
        snapshot

        ; New playable
        (assoc snapshot :prepared
               {:info item
                :playable (ap/create-playable provider-obj account item)})))

    (throw (ex-info "No such provider" {:id provider}))))


; ======= Public interface ================================

(defn prepare! [{:keys [account item]}]
  (swap! state prepare-snapshot item account))

(defn play! [{:keys [item account config]}]
  (swap!
    state
    (fn [{old :playable :as snapshot}]
      (when old
        (.removeAllListeners
          (player/events old))
        (player/close old))

      (let [{{:keys [playable]} :prepared
             :as snapshot} (prepare-snapshot snapshot item account)]
        (log/debug "playing prepared: " playable)
        (doto playable
          (listen-for-events)
          (apply-config config)
          (player/play))
        (println "playable <- " playable)
        (assoc snapshot :playable playable))))
  (println "playing!"))

(defn unpause! []
  (on-playable player/play))

(defn pause! []
  (on-playable player/pause))

(defn set-volume! [volume-percent]
  (on-playable player/set-volume volume-percent))

(comment
  (prepare! {:item {:id "8FV4gcs-MNA"
                    :provider :ytm}})

  (play! {:item {:id "8FV4gcs-MNA"
                 :provider :ytm}})

  (pause!)

  )
