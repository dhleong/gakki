(ns gakki.player.track.core
  (:require [archetype.util :refer [>evt]]
            [promesa.core :as p]
            [gakki.player.clip :as clip :refer [IAudioClip]]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.stream.seek :as seek]
            [gakki.util.logging :as log]))

(def ^:private log (log/of :player/track))

(defprotocol IAudioTrack
  (id [this])
  (close [this])
  (read-config [this])
  (seek [this timestamp-seconds]))

(defn- create-clip [{:keys [clip seek-time seek-bytes] :as current-state}
                    config stream]
  ; Just in case, if there's any old clip lingering, close it:
  (when clip
    (clip/close clip))

  (let [full-config (assoc config :start-time-seconds (or seek-time 0))
        clip (clip/from-stream
               (seek/nbytes-chunkwise stream (or seek-bytes 0))
               full-config)]
    (log "playing " stream " with " full-config
         "; seek-bytes=" seek-bytes)
    (clip/set-volume clip (:volume current-state 1.0))
    (-> (clip/play clip)
        (p/catch
          (fn [e]
            (case (:kind (ex-data e))
              :warning nil ; ignore
              (log/error "Error playing clip: " (ex-message e) (ex-data e))))))
    (assoc current-state :clip clip)))

(defn- swap-close! [state & {:keys [closed?]
                             :or {closed? true}}]
  (swap! state (fn [{:keys [clip] :as state}]
                 (when clip
                   (clip/close clip))
                 (-> state
                     (dissoc :clip)
                     (assoc :closed? closed?)))))

(defn- restart-clip! [track]
  (let [seek-time (clip/current-time track)]
    ; Close this clip (without marking this Track as closed),
    ; seek to where we were, and start again
    (swap-close! (.-state track) :closed? false)
    (p/do!
      (seek track seek-time)
      (clip/play track))))

(defn- play! [this clip]
  (-> (clip/play clip)
      (p/catch
        (fn [e]
          (case (:kind (ex-data e))
            :system (do (log/error "SYSTEM error playing clip; possible device change")
                        (restart-clip! this))
            :warning nil ; ignore
            (log/error "Error playing clip: " (ex-message e) (ex-data e)))))))

(deftype AudioTrack [id, ^IPCMSource source, state]
  Object
  (toString [_this]
    (str "AudioTrack(" id ": " source ")"))

  IPrintWithWriter
  (-pr-writer [this writer _]
    (-write writer (.toString this)))

  IAudioTrack
  (id [_this] id)

  (close [this]
    (log this " .close")
    (swap-close! state))

  (read-config [_this]
    (pcm/read-config source))

  (seek [this timestamp-seconds]
    (p/let [max-duration (pcm/seekable-duration source)
            timestamp-seconds (min max-duration timestamp-seconds)
            bytes-to-skip (pcm/duration-to-bytes source timestamp-seconds)
            was-playing? (clip/playing? this)]
      ; Simply close any existing clip, reconfigure our start-time,
      ; and start again:
      (swap-close! state :closed? false)
      (swap! state assoc
             :seek-time timestamp-seconds
             :seek-bytes bytes-to-skip)
      (when (and was-playing?
                 (not (:closed? @state)))
        (clip/play this))))

  IAudioClip
  (close [this] (close this)) ; delegate to the IAudioTrack implementation above

  (current-time [_this]
    (if-let [clip (:clip @state)]
      (clip/current-time clip)
      0))

  (default-output-device? [_this]
    (when-let [clip (:clip @state)]
      (clip/default-output-device? clip)))

  (playing? [_this]
    (if-let [clip (:clip @state)]
      (clip/playing? clip)
      false))

  (play [this]
    (let [{:keys [clip closed?]} @state]
      (if-not closed?
        (cond
          (and clip (clip/default-output-device? clip))
          (do (log this " Still default device; resume")
              (play! this clip))

          ; We have a clip, but it's targeting the wrong device
          clip
          (restart-clip! this)

          :else
          (p/plet [stream (pcm/open-read-stream source)
                   config (pcm/read-config source)]
            (if (:closed? @state)
              (log this " Resolved stream and config when already closed")
              (do
                (>evt [:player/on-playback-config-resolved id config])
                (swap! state create-clip config stream)))))
        (log this " Received (play) while closed"))))

  (pause [this]
    (when-let [clip (:clip @state)]
      (log "Pausing clip " clip " from " this)

      (if-not (clip/default-output-device? clip)
        (do
          ; NOTE: As noted in the AudioClip implementation, if we try to
          ; just pause the clip when its output device no longer exists, the
          ; process will hang, so we have to just close it in this case.
          (log this " closing clip (no longer default device)")
          (clip/close clip)
          (swap! state dissoc :clip))

        (clip/pause clip))))

  (set-volume [_this volume-percent]
    (swap! state assoc :volume volume-percent)

    (when-let [clip (:clip @state)]
      (clip/set-volume clip volume-percent))))

(defn create [id, ^IPCMSource source]
  (->AudioTrack id source (atom nil)))


