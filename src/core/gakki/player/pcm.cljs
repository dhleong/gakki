(ns gakki.player.pcm
  (:require [applied-science.js-interop :as j]
            ["audify" :refer [RtAudio RtAudioFormat]]
            ["events" :refer [EventEmitter]]
            ["stream" :refer [Writable]]
            [gakki.player.core :as player :refer [IPlayable]]))

(defn- ->writable-stream [^RtAudio speaker]
  (Writable. #js {:write (fn write [chnk _encoding callback]
                           (.write speaker chnk)
                           (callback))}))

(deftype PcmStreamPlayable [state, events, ^js stream]
  IPlayable
  (events [_this] events)

  (play [_this]
    (if-let [output-stream (:output-stream @state)]
      (let [speaker (:speaker @state)]
        (println "UNPAUSE")
        (.pipe stream output-stream)
        (.start speaker))

      (let [speaker ((:create-speaker @state))]
        (println "PLAY")
        (swap! state assoc
               :speaker speaker
               :output-stream (let [output-stream (->writable-stream speaker)]
                                (.pipe stream output-stream)
                                output-stream)))))

  (pause [_this]
    (let [{:keys [^RtAudio speaker, output-stream]} @state]
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
  ([^EventEmitter events, {:keys [channels frame-size sample-rate]}, ^js stream]
   (let [on-error (fn on-error [kind e]
                    ; TODO log?
                    (println "PCM Stream Error [" kind "] " e))

         create-speaker #(let [instance (RtAudio.)]

                           (js/setInterval
                             (fn [] (println (j/get instance :streamTime)))
                             1000)

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
       #_(.on "close" #(do
                       (println "STREAM END")
                       #_(.emit events "end")))
       (.on "error" on-error))

     (->PcmStreamPlayable
       (atom {:create-speaker create-speaker})
       events
       stream))))
