(ns gakki.player
  "Player service for Gakki. To improve the reliability of the main
   process and to avoid debug output from low-level libraries
   corrupting the main CLI UI, audio playback is delegated to a
   separate, child process that is communicated with over IPC"
  (:require [cognitect.transit :as t]
            [gakki.player.core :as player]
            [gakki.player.ytm :refer [youtube-id->playable]]))

(defonce ^:private state (atom nil))

(defn init []
  (println "Started"))

(defn pause [_]
  (when-let [playable (:playable @state)]
    (player/pause playable)))

(defn play [{:keys [provider info]}]
  (swap! state update :playable

         (fn [old]
           (when old
             (player/close old))

           ; TODO: delegate by provider (keyword ID)
           (doto (youtube-id->playable (:id info))
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
