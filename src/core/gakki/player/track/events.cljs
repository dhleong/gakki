(ns gakki.player.track.events
  (:require ["events" :refer [EventEmitter]]
            [gakki.player.core :refer [IPlayable]]
            [gakki.player.clip :as clip :refer [IAudioClip]]
            [gakki.player.track.core :as track :refer [IAudioTrack]]
            [gakki.util.logging :as log]
            [promesa.core :as p]))

(defn- enqueue-end-notification [^EventEmitter events config ^IAudioClip clip]
  (let [end-timeout (max 0
                         (- (:duration config)
                            (clip/current-time clip)
                            500))

        ending-timeout (max 0
                            (- end-timeout 5000))

        end-timer (js/setTimeout
                    #(.emit events "end")
                    end-timeout)
        ending-timer (js/setTimeout
                       #(.emit events "ending")
                       ending-timeout) ]
    (log/debug "Notifying end of file after " (/ end-timeout 1000) "s"
               "; ending after " (/ ending-timeout 1000) "s"
               "(duration=" (:duration config) ")")
    (fn clear-timer []
      (js/clearTimeout end-timer)
      (js/clearTimeout ending-timer))))

(deftype EventfulAudioTrack [^IAudioTrack base, ^EventEmitter events, state]
  IAudioTrack
  (close [_this] (track/close base))
  (read-config [_this] (track/read-config base))
  (seek [_this timestamp-seconds] (track/seek base timestamp-seconds))

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
      (p/let [config (track/read-config this)]
        (swap! state (fn [{:keys [clear-timer] :as old-state}]
                       (when clear-timer
                         (clear-timer))
                       (assoc old-state :clear-timer
                              (enqueue-end-notification
                                events
                                config
                                this)))))
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
