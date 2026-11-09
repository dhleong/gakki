(ns gakki.player.clip
  (:require ["audify" :default audify :refer [RtAudioErrorType RtAudioFormat]]
            ["events" :refer [EventEmitter]]
            ["stream" :refer [Readable Writable]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [gakki.const :as const]
            [gakki.player.stream.resampling :as resampling]
            [gakki.player.util :refer-macros [with-consuming-error]]
            [gakki.util.logging :as log]))

(def ^:private RtAudio (.-RtAudio audify))

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

(defn- device-by-id
  ([^RtAudio speaker, id]
   (device-by-id speaker id nil))
  ([^RtAudio speaker, id opts]
   (let [all-devices (.getDevices speaker)]
     (letfn [(debug-object []
               {:instance speaker
                :devices (map #(js->clj % :keywordize-keys true) all-devices)
                :id id
                :fatal? true})]
       (or (some->
            (some (fn [device]
                    (when (= id (j/get device .-id))
                      device))
                  all-devices)
            (js->clj :keywordize-keys true))

           (when (:required? opts)
             (throw (ex-info (str "Unable to load output device #" id)
                             (debug-object))))

           (log/error "Unable to load output device #" id (debug-object)))))))

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

(defn- target-config
  "Returns the config we'd *like* to open the device with, given the
   source config"
  [{available-rates :sampleRates
    available-channels :outputChannels
    preferred-rate :preferredSampleRate
    :as device}
   {:keys [channels sample-rate] :as config}]
  (if (and (some (partial = sample-rate) available-rates)
           (= channels available-channels))
    config

    (if-let [desired-rate (pick-desired-rate device)]
      (assoc config
             :sample-rate desired-rate
             :channels (min available-channels channels))

      (throw (ex-info "No sample rate available?"
                      {:available-rates available-rates
                       :preferred-rate preferred-rate
                       :config config})))))

; Some devices (notably aggregate devices like a "Multi-Output Device" that
; includes a Bluetooth headset in hands-free mode) advertise sample rates that
; they cannot actually switch to. Each failed attempt costs RtAudio ~2s, so we
; remember which rate actually worked for a given device configuration.
(defonce ^:private working-device-rates (atom {}))

(defn- device-signature [device]
  (select-keys device [:name :outputChannels :sampleRates]))

(defn- candidate-configs [device config]
  (let [base (target-config device config)
        known (get @working-device-rates (device-signature device))]
    ; Try any rate known to work for this device, then the ideal rate, then
    ; fall back through the remaining rates from best to worst quality.
    ; NOTE: `known` and the base rate both come from the device's advertised
    ; :sampleRates, so no further filtering is needed.
    (->> (concat (remove nil? [known (:sample-rate base)])
                 (sort > (:sampleRates device)))
         distinct
         (map #(assoc base :sample-rate %)))))

(defn- sample-rate-error? [e]
  (some-> (ex-message e)
          (str/includes? "sample rate")))

(defn- resample-if-needed [{source-rate :sample-rate source-channels :channels
                            :as source-config}
                           {:keys [sample-rate channels] :as config}
                           ^Readable stream]
  (if (= (select-keys source-config [:sample-rate :channels])
         (select-keys config [:sample-rate :channels]))
    stream

    (do
      ((log/of :player/clip)
       "Resampling:"
       " SR " source-rate " -> " sample-rate
       " CH " source-channels " -> " channels)
      (resampling/convert-pcm-config stream source-config config))))

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
     (partial on-error events))))

(defn- open-stream-with-fallback
  "Attempts to open the stream with each candidate config in turn, falling
   back to other sample rates if the device refuses to switch rates.
   Returns the config that was successfully opened."
  [^RtAudio instance, ^EventEmitter events, device, device-id, source-config]
  (loop [[config & remaining] (candidate-configs device source-config)]
    (let [result (try
                   (open-stream instance events device device-id config)
                   config
                   (catch :default e
                     (if (and (seq remaining)
                              (sample-rate-error? e))
                       (do
                         ((log/of :player/clip)
                          "Failed to open @ " (:sample-rate config)
                          "; trying fallback rates..." (ex-message e))
                         ::retry)

                       (do
                         (log/error "Failed to initialize AudioClip"
                                    {:config config
                                     :device device}
                                    e)
                         (throw e)))))]
      (if (= ::retry result)
        (recur remaining)

        (do
          (swap! working-device-rates assoc
                 (device-signature device) (:sample-rate result))
          result)))))

(defn from-stream [^Readable stream, {:keys [start-time-seconds]
                                      :or {start-time-seconds 0}
                                      :as config}]
  (let [instance (RtAudio.)
        device-id (.getDefaultOutputDevice instance)
        device (device-by-id instance device-id {:required? true})
        events (EventEmitter.)
        source-config config
        config (open-stream-with-fallback
                instance events device device-id source-config)
        stream (resample-if-needed source-config config stream)
        instance (doto instance
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

  (let [instance (RtAudio.)]
    (try
      (println (device-by-id instance 42))
      ; (println (device-by-id instance 42 {:required? true}))
      (catch :default e
        (println "ERROR: " e))
      (finally
        (.closeStream instance)))))
