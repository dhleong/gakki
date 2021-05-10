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
    (.on "end" (fn on-end []
                 (when (identical? (:playable @state)
                                   playable)
                   (send! {:type :playable-end}))))))


; ======= Event handlers ==================================

(defn pause [_]
  (when-let [playable (:playable @state)]
    (player/pause playable)))

(defn play [{:keys [provider info]}]
  (swap! state update :playable

         (fn [old]
           (when old
             (player/close old))

           (if-let [provider-obj (get providers provider)]
             ; TODO: delegate by provider (keyword ID))
             (doto (ap/create-playable provider-obj info)
               (listen-for-events)
               (player/play))

             (throw (ex-info "No such provider" {:id provider}))))))

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
