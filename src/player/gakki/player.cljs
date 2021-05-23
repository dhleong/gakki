(ns gakki.player
  "Player service for Gakki. To improve the reliability of the main
   process and to avoid debug output from low-level libraries
   corrupting the main CLI UI, audio playback is delegated to a
   separate, child process that is communicated with over IPC"
  (:require [cognitect.transit :as t]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.core :as player]))

(defonce ^:private state (atom nil))

(def ^:private writer (t/writer :json))

(defn init []
  (println "Started"))

(defn- send! [msg]
  (->> msg
       (t/write writer)
       (js/process.send)))

(defn- listen-for-events [playable]
  (doto (player/events playable)
    (.once "ending" (fn on-ending []
                      (send! {:type :playable-ending})))
    (.once "end" (fn on-end []
                   (send! {:type :playable-end})))))

(defn- apply-config [playable config]
  (when-let [volume (:volume-percent config)]
    (player/set-volume playable volume)))


; ======= Event handlers ==================================

(defn pause [_]
  (when-let [playable (:playable @state)]
    (player/pause playable)))

(defn prepare! [{:keys [provider info]}]
  ; NOTE: this may be side-effecting: the provider will probably
  ; create a caching Playable which will begin to pre-fetch the file
  (if-let [provider-obj (get providers provider)]
    (let [{existing-info :info existing-playable :playable} (:prepared @state)]
      (if (= existing-info info)
        ; Reuse the existing, prepared playable for this (it may be prefetching
        ; and or have started prefetching)
        existing-playable

        ; New playable
        (let [new-playable (ap/create-playable provider-obj info)]
          (swap! state assoc :prepared {:info info
                                        :playable new-playable})
          new-playable)))

    (throw (ex-info "No such provider" {:id provider}))))

(defn play [{:keys [_provider _info config] :as args}]
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

(defn set-volume [{:keys [level]}]
  (when-let [playable (:playable @state)]
    (player/set-volume playable level)))

(defn unpause [_]
  (when-let [playable (:playable @state)]
    (player/play playable)))

(def ^:private handlers
  {:init #'init
   :pause #'pause
   :play #'play
   :prepare #'prepare!
   :set-volume #'set-volume
   :unpause #'unpause})


; ======= IPC bindings ====================================

(def ^:private reader (t/reader :json))

(defn handle-message [msg-raw]
  (let [{ty :type :as msg} (->> msg-raw
                                (t/read reader))]
    (if-let [handler (get handlers ty)]
      (do
        (println "Dispatching:" msg "to: " handler)
        (handler msg))

      (println "WARN: No handler for: " ty))))

(doto js/process
  ; NOTE: remove any previous message handlers in case this file
  ; gets hot-reloaded
  (.removeAllListeners "message")
  (.on "message" handle-message))
