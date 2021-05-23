(ns gakki.player.pcm
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat]]
            ["events" :refer [EventEmitter]]
            ["stream" :refer [Readable Writable]]
            [gakki.util.logging :as log]
            [gakki.player.core :as player :refer [IPlayable]]))

(defn- ->writable-stream [^RtAudio speaker]
  (Writable. #js {:write (fn write [chnk _encoding callback]
                           (.write speaker chnk)
                           (callback))}))

(defn- enqueue-end-notification [^EventEmitter events config speaker]
  (let [timeout (max 0
                     (- (:duration config)

                        (when speaker
                          (* (j/get speaker :streamTime)
                             1000))

                        500))]
    (log/debug "Notifying end of file after " (/ timeout 1000) "s"
               "(duration=" (:duration config) ")")
    (js/setTimeout
      #(.emit events "end")
      timeout)))

(deftype PcmStreamPlayable [state events config ^Readable stream]
  IPlayable
  (events [_this] events)

  (play [_this]
    ; clear any existing timer... just in case
    (swap! state update :timer js/clearTimeout)

    (if-let [output-stream (:output-stream @state)]
      (let [speaker (:speaker @state)]
        (swap! state assoc :timer (enqueue-end-notification events config speaker))
        (.pipe stream output-stream)
        (.start speaker))

      (let [speaker ((:create-speaker @state))]
        (swap! state assoc
               :speaker speaker
               :timer (enqueue-end-notification events config speaker)
               :output-stream (let [output-stream (->writable-stream speaker)]
                                (.pipe stream output-stream)
                                output-stream)))))

  (pause [_this]
    (let [{:keys [^RtAudio speaker, output-stream timer]} @state]
      (js/clearTimeout timer)
      (when (and speaker output-stream)
        (.unpipe stream output-stream)

        (doto speaker
          (.stop)))))

  (close [this]
    (player/pause this))

  (set-volume [_this level]
    (j/assoc! (:speaker @state) .-outputVolume level)))

(defn pcm-stream->playable
  "Create a Playable from a config map and a PCM stream"
  ([config, ^js stream] (pcm-stream->playable (EventEmitter.) config stream))
  ([^EventEmitter events
    {:keys [channels frame-size sample-rate] :as config}
    ^Readable stream]
   (let [on-error (fn on-error [kind e]
                    ; TODO log?
                    (log/debug "PCM Stream Error [" kind "] " e))

         create-speaker #(let [instance (RtAudio.)]
                           (doto instance
                             (.openStream
                               ; Output stream:
                               #js {:deviceId (.getDefaultOutputDevice instance)
                                    :nChannels channels }
                               nil ; No input stream
                               (.-RTAUDIO_SINT16 RtAudioFormat)
                               sample-rate
                               (or frame-size 960)
                               "gakki"
                               nil ; input callback
                               nil ; output callback
                               0 ; stream flags
                               on-error)
                             (.start)))]

     (doto stream
       (.on "error" on-error))

     (->PcmStreamPlayable
       (atom {:create-speaker create-speaker})
       events
       config
       stream))))
