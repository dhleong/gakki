(ns gakki.player.clip
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat]]
            [gakki.const :as const]
            ["stream" :refer [Readable Writable]]
            [gakki.util.logging :as log]))

(defn- ->writable-stream [^RtAudio speaker]
  (Writable. #js {:write (fn write [chnk _encoding callback]
                           (.write speaker chnk)
                           (callback))}))

(defn- device-by-id [^RtAudio speaker, id]
  (-> (.getDevices speaker)
      (nth id)
      (js->clj :keywordize-keys true)))

(defprotocol IAudioClip
  "An AudioClip represents a forward-only stream of audio starting
   from *some* point in time"
  (close [this])
  (current-time [this])
  (default-output-device? [this]
    "Returns true if this clip is configured to play on the system's
     *current* default output device.")
  (play [this])
  (playing? [this])
  (pause [this])
  (set-volume [this volume-percent]))

(deftype AudioClip [^Readable input, ^RtAudio speaker, device, ^Writable output]
  IAudioClip
  (close [_this]
    ; NOTE: If the output device has gone away (eg: disconnecting a bluetooth
    ; headset) trying to (.stop) the speaker will *hang*, so we just close it
    (.unpipe input output)
    (.closeStream speaker))

  (current-time [_this]
    (j/get speaker :streamTime))

  (default-output-device? [_this]
    (let [current-default (device-by-id speaker
                                        (.getDefaultOutputDevice speaker))]
      (= device current-default)))

  (play [this]
    (when-not (playing? this)
      (.pipe input output)
      (.start speaker)))

  (playing? [_this]
    (.isStreamRunning speaker))

  (pause [this]
    (when (playing? this)
      (.unpipe input output)
      (.stop speaker)))

  (set-volume [_this volume-percent]
    (j/assoc! speaker .-outputVolume volume-percent)))

(defn from-stream [^Readable stream, {:keys [channels sample-rate frame-size
                                             start-time-seconds]
                                      :or {start-time-seconds 0}}]
  (let [on-error (fn on-error [kind e]
                   ; TODO log?
                   (log/debug "PCM Stream Error [" kind "] " e))
        instance (RtAudio.)
        device-id (.getDefaultOutputDevice instance)
        instance (doto instance
                   (.openStream
                     ; Output stream:
                     #js {:deviceId device-id
                          :nChannels channels}
                     nil ; No input stream
                     (.-RTAUDIO_SINT16 RtAudioFormat)
                     sample-rate
                     (or frame-size const/default-frame-size)
                     "gakki" ; stream name
                     nil ; input callback
                     nil ; output callback
                     0 ; stream flags
                     on-error)
                   (j/assoc! :streamTime start-time-seconds))
        device (device-by-id instance device-id)]
    (->AudioClip stream instance device (->writable-stream instance))))
