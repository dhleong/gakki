(ns gakki.player
  (:require [archetype.util :refer [>evt]]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.core :as player]))

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


; ======= Public interface ================================

(defn prepare! [{{provider :provider :as item} :item :keys [account]}]
  (if-let [provider-obj (get providers provider)]
    (let [{existing-info :info existing-playable :playable} (:prepared @state)]
      (if (= existing-info item)
        ; Reuse the existing, prepared playable for this (it may be prefetching
        ; and or have started prefetching)
        existing-playable

        ; New playable
        (let [new-playable (ap/create-playable provider-obj account item)]
          (swap! state assoc :prepared {:info item
                                        :playable new-playable})
          new-playable)))

    (throw (ex-info "No such provider" {:id provider}))))

(defn play! [{:keys [_item _account config] :as args}]
  (swap! state update :playable

         (fn [old]
           (when old
             (.removeAllListeners
               (player/events old))
             (player/close old))

           (doto (prepare! args)
             (listen-for-events)
             (apply-config config)
             (player/play)))))

(defn unpause! []
  (when-let [playable (:playable @state)]
    (player/play playable)))

(defn pause! []
  (when-let [playable (:playable @state)]
    (player/pause playable)))

(defn set-volume! [volume-percent]
  (when-let [playable (:playable @state)]
    (player/set-volume playable volume-percent)))
