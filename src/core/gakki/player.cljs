(ns gakki.player
  (:require [archetype.util :refer [>evt]]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.clip :as clip]
            [gakki.player.track.core :as track :refer [IAudioTrack]]
            [gakki.player.core :as player]
            [gakki.util.logging :as log]
            [promesa.core :as p]))

(defonce ^:private state (atom nil))

(defn- on-playable-end []
  (>evt [:player/event {:type :playable-end}]))

(defn- listen-for-events [playable]
  (doto (player/events playable)
    (.once "ending" (fn on-ending []
                      (>evt [:player/event {:type :playable-ending}])))
    (.once "end" on-playable-end)))

(defn- apply-config [playable config]
  (when-let [volume (:volume-percent config)]
    (player/set-volume playable volume)))

(defn- catch-error [p playable f]
  (p/catch p (fn [e]
               (log/error "Failed to " f " with " playable e)
               (let [events (player/events playable)]
                 (when (> (.listenerCount events "end") 0)
                   (.removeAllListeners events "end")
                   (on-playable-end))))))

(defn- on-playable [f & args]
  (when-let [playable (:playable @state)]
    (-> (apply f playable args)
        (catch-error playable f))))

(defn- play-catching [playable]
  (-> (player/play playable)
      (catch-error playable "play")))

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

(defn- seek-relative [^IAudioTrack track, relative-seconds]
  (let [from (clip/current-time track)]
    (track/seek track (-> (+ from relative-seconds)
                          (max 0)))))

; ======= Public interface ================================

(defn current-time []
  (or (when-let [playable (:playable @state)]
        (clip/current-time playable))
      0))

(defn prepare! [{:keys [account item]}]
  (swap! state prepare-snapshot item account))

(defn play! [{:keys [item account config]}]
  (swap!
    state
    (fn [{old :playable :as snapshot}]
      (when old
        (.removeAllListeners
          (player/events old))
        (-> (player/close old)
            (catch-error old "close")))

      (let [{{:keys [playable]} :prepared
             :as snapshot} (prepare-snapshot snapshot item account)]
        (doto playable
          (listen-for-events)
          (apply-config config)
          (play-catching))
        (-> snapshot
            (assoc :playable playable)
            (dissoc :prepared))))))

(defn unpause! []
  (on-playable player/play))

(defn pause! []
  (on-playable player/pause))

(defn seek-by! [relative-seconds]
  (on-playable seek-relative relative-seconds))

(defn seek-to! [timestamp-seconds]
  (on-playable track/seek timestamp-seconds))

(defn set-volume! [volume-percent]
  (on-playable player/set-volume volume-percent))

(comment
  (prepare! {:item {:id "8FV4gcs-MNA"
                    :provider :ytm}})

  (play! {:item {:id "8FV4gcs-MNA"
                 :provider :ytm}})

  (pause!)

  )
