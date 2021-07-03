(ns gakki.player.clip
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat]]
            ["stream" :refer [Readable Writable]]
            [gakki.const :as const]
            [gakki.util.logging :as log]
            [gakki.player.stream.resampling :as resampling]))

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
  (current-time [this]
    "Returns the current playback time of this clip in seconds")
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

(defn- resample-if-needed
  [{available-rates :sampleRates
    available-channels :outputChannels
    preferred-rate :preferredSampleRate}
   {:keys [channels sample-rate] :as config}
   ^Readable stream]
  (if (and (some (partial = sample-rate) available-rates)
           (<= channels available-channels))
    [config stream]

    (if-let [desired-rate (or preferred-rate
                              (peek available-rates))]
      (let [new-config (assoc config
                              :sample-rate desired-rate
                              :channels (min available-channels channels))]
        ((log/of :player/clip)
         "Resampling:"
         " SR " sample-rate " -> " desired-rate
         " CH " channels " -> " available-channels)
        [new-config
         (resampling/convert-pcm-config stream config new-config)])

      (throw (ex-info "No sample rate available?"
                      {:available-rates available-rates
                       :preferred-rate preferred-rate
                       :config config})))))

(defn- on-error [kind e]
  ((log/of :player/clip) "PCM Stream Error [" kind "] " e))

(defn- open-stream [^RtAudio instance, device, device-id,
                    {:keys [channels sample-rate frame-size]
                     :as config}]
  (try
    (doto instance
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
        on-error))
    (catch :default e
      (log/error "Failed to initialize AudioClip"
                 {:config config
                  :device device}
                 e)

      ; Re-throw... for now
      (throw e))))

(defn from-stream [^Readable stream, {:keys [start-time-seconds]
                                      :or {start-time-seconds 0}
                                      :as config}]
  (let [instance (RtAudio.)
        device-id (.getDefaultOutputDevice instance)
        device (device-by-id instance device-id)
        [config stream] (resample-if-needed device config stream)
        instance (doto instance
                   (open-stream device device-id config)
                   (j/assoc! :streamTime start-time-seconds)) ]
    (->AudioClip stream instance device (->writable-stream instance))))
