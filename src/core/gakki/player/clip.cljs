(ns gakki.player.clip
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat RtAudioErrorType]]
            ["events" :refer [EventEmitter]]
            ["stream" :refer [Readable Writable]]
            [gakki.const :as const]
            [gakki.util.logging :as log]
            [gakki.player.stream.resampling :as resampling]
            [gakki.player.util :refer-macros [with-consuming-error]]))

(def ^:private error-kinds
  {(.-WARNING RtAudioErrorType) :warning
   (.-DEBUG_WARNING RtAudioErrorType) :warning
   (.-UNSPECIFIED RtAudioErrorType) :other
   (.-NO_DEVICES_FOUND RtAudioErrorType) :no-device
   (.-INVALID_DEVICE RtAudioErrorType) :invalid-device
   (.-INVALID_PARAMETER RtAudioErrorType) :parameter
   (.-INVALID_USE RtAudioErrorType) :use
   (.-MEMORY_ERROR RtAudioErrorType) :memory
   (.-DRIVER_ERROR RtAudioErrorType) :driver
   (.-SYSTEM_ERROR RtAudioErrorType) :system
   (.-THREAD_ERROR RtAudioErrorType) :thread})

(defn- ->writable-stream [^RtAudio speaker on-error]
  (Writable. #js {:write (fn write [chnk _encoding callback]
                           (try
                             (.write speaker chnk)
                             (catch :default e
                               (on-error e)
                               (throw e)))
                           (callback))}))

(defn- device-by-id [^RtAudio speaker, id]
  (-> (.getDevices speaker)
      (nth id)
      (js->clj :keywordize-keys true)))

(defn- default-output-device [^RtAudio speaker]
  (device-by-id speaker (.getDefaultOutputDevice speaker)))

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

(deftype AudioClip [^Readable input,
                    ^RtAudio speaker,
                    ^EventEmitter speaker-events,
                    device,
                    ^Writable output]
  IAudioClip
  (close [_this]
    ; NOTE: If the output device has gone away (eg: disconnecting a bluetooth
    ; headset) trying to (.stop) the speaker will *hang*, so we just close it
    (.unpipe input output)
    (.closeStream speaker))

  (current-time [_this]
    (j/get speaker :streamTime))

  (default-output-device? [_this]
    (let [current-default (default-output-device speaker)]
      (= device current-default)))

  (play [this]
    (when-not (playing? this)
      (with-consuming-error speaker-events
        (.pipe input output)
        (.start speaker))))

  (playing? [_this]
    (.isStreamRunning speaker))

  (pause [this]
    (when (playing? this)
      (.unpipe input output)
      (.stop speaker)))

  (set-volume [_this volume-percent]
    (j/assoc! speaker .-outputVolume volume-percent)))

(defn- pick-desired-rate [{available-rates :sampleRates
                           available-channels :outputChannels
                           preferred-rate :preferredSampleRate}]
  ; NOTE: This is a bit of a kludge, but experimentally if a device supports only
  ; mono output, it *may not* actually support its advertised preferred sample rate,
  ; resulting in a "timeout waiting for sample rate update for device" error. In
  ; this case, we prefer the lowest available sample rate, which seems to work.
  (if (= 1 available-channels)
    (first available-rates)

    (or preferred-rate
        (peek available-rates))))

(defn- resample-if-needed
  [{available-rates :sampleRates
    available-channels :outputChannels
    preferred-rate :preferredSampleRate
    :as device}
   {:keys [channels sample-rate] :as config}
   ^Readable stream]
  (if (and (some (partial = sample-rate) available-rates)
           (= channels available-channels))
    [config stream]

    (if-let [desired-rate (pick-desired-rate device)]
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

(defn- on-error [^EventEmitter events, kind ^String error-message]
  (let [has-consumer? (> (.listenerCount events "error") 0)]
    ((log/of :player/clip)
     "PCM Stream Error [" kind "] "
     "( consumed?" has-consumer? ")"
     error-message)
    (when has-consumer?
      (.emit events "error" (ex-info error-message
                                     {:kind (get error-kinds kind :other)})))))

(defn- open-stream [^RtAudio instance, ^EventEmitter events, device, device-id,
                    {:keys [channels sample-rate frame-size]
                     :as config}]
  (try
    ((log/of :player/clip) "opening stream for " config " @ " device)
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
        (partial on-error events)))
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
        events (EventEmitter.)
        instance (doto instance
                   (open-stream events device device-id config)
                   (j/assoc! :streamTime start-time-seconds))
        on-write-error (fn [e]
                         (log/error "Error in Clip Writable stream"
                                    {:config config}
                                    e))]
    (->AudioClip stream instance events device
                 (->writable-stream instance on-write-error))))

(comment

  (let [instance (RtAudio.)
        device (default-output-device instance)]
    (.closeStream instance)
    {:device device
     :rate (pick-desired-rate device)
     :channels (:outputChannels device)})

  )
