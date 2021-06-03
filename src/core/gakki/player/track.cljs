(ns gakki.player.track
  (:require [gakki.util.logging :as log]
            [promesa.core :as p]
            [gakki.player.clip :as clip :refer [IAudioClip]]
            [gakki.player.pcm2 :as pcm :refer [IPCMSource]]
            [gakki.player.seek :as seek]))

(defprotocol IAudioTrack
  (close [this])
  (seek [this timestamp-seconds]))

(deftype AudioTrack [^IPCMSource source, state]
  IAudioTrack
  (close [_this]
    (swap! state (fn [{:keys [clip] :as state}]
                   (when clip
                     (clip/close clip))
                   (dissoc state :clip))))

  (seek [this timestamp-seconds]
    (p/let [max-duration (pcm/seekable-duration source)
            timestamp-seconds (min max-duration timestamp-seconds)
            bytes-to-skip (pcm/duration-to-bytes source timestamp-seconds)]
      ; Simply close any existing clip, reconfigure our start-time,
      ; and start again:
      (close this)
      (swap! state assoc
             :seek-time timestamp-seconds
             :seek-bytes bytes-to-skip)
      (clip/play this)))

  IAudioClip
  (current-time [_this]
    (if-let [clip (:clip @state)]
      (clip/current-time clip)
      0))

  (play [_this]
    (if-let [clip (:clip @state)]
      (clip/play clip)

      (p/let [stream (pcm/open-read-stream source)
              config (pcm/read-config source)]
        (swap!
          state
          (fn create-clip [{:keys [seek-time seek-bytes] :as current-state}]
            (let [full-config (assoc config :start-time-seconds (or seek-time 0))
                  clip (clip/from-stream
                         (seek/nbytes stream (or seek-bytes 0))
                         full-config)]
              (log/debug "playing " stream " with " full-config
                         "; seek-bytes=" seek-bytes)
              (doto clip
                (clip/set-volume (:volume current-state 1.0))
                (clip/play))
              (assoc current-state :clip clip)))))))

  (pause [_this]
    (when-let [clip (:clip @state)]
      (clip/pause clip)))

  (set-volume [_this volume-percent]
    (swap! state assoc :volume volume-percent)

    (when-let [clip (:clip @state)]
      (clip/set-volume clip volume-percent))))

(defn create [^IPCMSource source]
  (->AudioTrack source (atom nil)))


(defonce test-track (atom nil))

(comment

  (swap! test-track (fn [old]
                 (when old
                   (log/debug "Closing old " old)
                   (close old))

                 (create
                   (pcm/create-disk-source
                     "/Users/daniel/Library/Caches/gakki/ytm.2mqi6Vqfhh8"))))


  (close @test-track)

  (clip/play @test-track)
  (clip/pause @test-track)
  (seek @test-track 102)

  )
