(ns gakki.player.track.core
  (:require [gakki.util.logging :as log]
            [promesa.core :as p]
            [gakki.player.clip :as clip :refer [IAudioClip]]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.stream.seek :as seek]))

(defprotocol IAudioTrack
  (close [this])
  (read-config [this])
  (seek [this timestamp-seconds]))

(deftype AudioTrack [^IPCMSource source, state]
  IAudioTrack
  (close [_this]
    (swap! state (fn [{:keys [clip] :as state}]
                   (when clip
                     (clip/close clip))
                   (dissoc state :clip))))

  (read-config [_this]
    (pcm/read-config source))

  (seek [this timestamp-seconds]
    (p/let [max-duration (pcm/seekable-duration source)
            timestamp-seconds (min max-duration timestamp-seconds)
            bytes-to-skip (pcm/duration-to-bytes source timestamp-seconds)
            was-playing? (clip/playing? this)]
      ; Simply close any existing clip, reconfigure our start-time,
      ; and start again:
      (close this)
      (swap! state assoc
             :seek-time timestamp-seconds
             :seek-bytes bytes-to-skip)
      (when was-playing?
        (clip/play this))))

  IAudioClip
  (close [this] (close this)) ; delegate to the IAudioTrack implementation above

  (current-time [_this]
    (if-let [clip (:clip @state)]
      (clip/current-time clip)
      0))

  (playing? [_this]
    (if-let [clip (:clip @state)]
      (clip/playing? clip)
      false))

  (play [this]
    (let [clip (:clip @state)]
      (cond
        (and clip (clip/default-output-device? clip))
        (clip/play clip)

        ; We have a clip, but it's targeting the wrong device
        clip
        (let [seek-time (clip/current-time this)]
          ; Close this clip, seek to where we were, and start again
          (close this)
          (seek this seek-time)
          (clip/play this))

        :else
        (p/plet [stream (pcm/open-read-stream source)
                 config (pcm/read-config source)]
          (swap!
            state
            (fn create-clip [{:keys [clip seek-time seek-bytes] :as current-state}]
              ; Just in case, if there's any old clip lingering, close it:
              (when clip
                (clip/close clip))

              (let [full-config (assoc config :start-time-seconds (or seek-time 0))
                    clip (clip/from-stream
                           (seek/nbytes-chunkwise stream (or seek-bytes 0))
                           full-config)]
                (log/debug "playing " stream " with " full-config
                           "; seek-bytes=" seek-bytes)
                (doto clip
                  (clip/set-volume (:volume current-state 1.0))
                  (clip/play))
                (assoc current-state :clip clip))))))))

  (pause [_this]
    (when-let [clip (:clip @state)]
      (clip/pause clip)))

  (set-volume [_this volume-percent]
    (swap! state assoc :volume volume-percent)

    (when-let [clip (:clip @state)]
      (clip/set-volume clip volume-percent))))

(defn create [^IPCMSource source]
  (->AudioTrack source (atom nil)))


