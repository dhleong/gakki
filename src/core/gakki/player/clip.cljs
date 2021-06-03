(ns gakki.player.clip
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat]]
            ["stream" :refer [Readable Writable]]
            [gakki.util.logging :as log]))

(defn- ->writable-stream [^RtAudio speaker]
  (Writable. #js {:write (fn write [chnk _encoding callback]
                           (.write speaker chnk)
                           (callback))}))

(defprotocol IAudioClip
  "An AudioClip represents a forward-only stream of audio starting
   from *some* point in time"
  (close [this])
  (current-time [this])
  (play [this])
  (pause [this])
  (set-volume [this volume-percent]))

(deftype AudioClip [^Readable input, ^RtAudio speaker, ^Writable output]
  IAudioClip
  (close [this]
    (pause this)
    (.closeStream speaker))

  (current-time [_this]
    (j/get speaker :streamTime))

  (play [_this]
    (when-not (.isStreamRunning speaker)
      (.pipe input output)
      (.start speaker)))

  (pause [_this]
    (when (.isStreamRunning speaker)
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
        instance (doto instance
                   (.openStream
                     ; Output stream:
                     #js {:deviceId (.getDefaultOutputDevice instance)
                          :nChannels channels}
                     nil ; No input stream
                     (.-RTAUDIO_SINT16 RtAudioFormat)
                     sample-rate
                     (or frame-size 960)
                     "gakki" ; stream name
                     nil ; input callback
                     nil ; output callback
                     0 ; stream flags
                     on-error)
                   (j/assoc! :streamTime start-time-seconds))]
    (->AudioClip stream instance (->writable-stream instance))))
