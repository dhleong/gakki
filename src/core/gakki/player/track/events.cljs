(ns gakki.player.track.events
  (:require ["events" :refer [EventEmitter]]
            [gakki.player.core :refer [IPlayable]]
            [gakki.player.clip :as clip :refer [IAudioClip]]
            [gakki.player.track.core :as track :refer [IAudioTrack]]
            [gakki.util.logging :as log]
            [promesa.core :as p]))

(def ^:private log (log/of :player.track/events))

(declare EventfulAudioTrack)

(defn- enqueue-end-notification
  [^EventEmitter events config ^IAudioClip clip current-timestamp-seconds]
  (let [current-time-ms (* (or current-timestamp-seconds
                               (clip/current-time clip))
                           1000)
        end-timeout (max 0
                         (- (:duration config)
                            current-time-ms
                            500))

        ending-timeout (max 0
                            (- end-timeout 5000))

        end-timer (js/setTimeout
                    #(.emit events "end")
                    end-timeout)
        ending-timer (js/setTimeout
                       #(.emit events "ending")
                       ending-timeout) ]
    (log "Notifying end of file after " (/ end-timeout 1000) "s"
         "; ending after " (/ ending-timeout 1000) "s"
         "(duration=" (/ (:duration config) 1000) "s"
         "; current-time" (/ current-time-ms 1000) "s)")
    (fn clear-timer []
      (js/clearTimeout end-timer)
      (js/clearTimeout ending-timer))))

(defn- restart-event-timers
  ([^EventfulAudioTrack eventful-track] (restart-event-timers eventful-track nil))
  ([^EventfulAudioTrack eventful-track current-timestamp-seconds]
   (p/let [config (track/read-config eventful-track)]
     (swap! (.-state eventful-track)
            (fn [{:keys [clear-timer] :as old-state}]
              (when clear-timer
                (clear-timer))
              (assoc old-state :clear-timer
                     (enqueue-end-notification
                       (.-events eventful-track)
                       config
                       eventful-track
                       current-timestamp-seconds)))))))

(deftype EventfulAudioTrack [^IAudioTrack base, ^EventEmitter events, state]
  Object
  (toString [_this]
    (str "Eventful" base))

  IPrintWithWriter
  (-pr-writer [this writer _]
    (-write writer (.toString this)))

  IAudioTrack
  (id [_this] (track/id base))
  (close [_this] (track/close base))
  (read-config [_this] (track/read-config base))
  (seek [this timestamp-seconds]
    (track/seek base timestamp-seconds)
    ; NOTE: clip/current-time doesn't seem to be reliable here for some reason,
    ; so we just pass the exact timestamp to use:
    (restart-event-timers this timestamp-seconds))

  IPlayable
  (close [this] (clip/close this))
  (events [_this] events)
  (set-volume [this volume-percent] (clip/set-volume this volume-percent))
  (play [this] (clip/play this))
  (pause [this] (clip/pause this))

  IAudioClip
  (close [this] (track/close this))
  (current-time [_this] (clip/current-time base))
  (default-output-device? [_this] (clip/default-output-device? base))
  (playing? [_this] (clip/playing? base))
  (set-volume [_this volume-percent] (clip/set-volume base volume-percent))

  (play [this]
    (when-not (clip/playing? this)
      (restart-event-timers this)
      (clip/play base)))

  (pause [this]
    (when (clip/playing? this)
      (swap! state (fn [{:keys [clear-timer] :as old-state}]
                     (when clear-timer
                       (clear-timer))
                     (dissoc old-state :clear-timer)))

      (clip/pause base))))

(defn wrap [^IAudioTrack base, ^EventEmitter events]
  (->EventfulAudioTrack base events (atom nil)))
