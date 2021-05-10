(ns gakki.player.pcm
  (:require ["events" :refer [EventEmitter]]
            ["speaker" :as Speaker]
            ["prism-media" :as prism]
            [gakki.player.core :as player :refer [IPlayable]]))

(deftype PcmStreamPlayable [state, events, ^js stream, ^js volume-ctrl]
  IPlayable
  (events [_this] events)

  (play [_this]
    (when-not (:speaker @state)
      (let [speaker ((:create-speaker @state))]
        (swap! state assoc :speaker speaker)
        (-> stream
            (.pipe volume-ctrl)
            (.pipe speaker)))))

  (pause [_this]
    (when-let [speaker (:speaker @state)]
      (swap! state dissoc :speaker)

      (doto volume-ctrl
        (.unpipe speaker))
      (doto stream
        (.unpipe volume-ctrl))

      (doto speaker
        (.end)
        (.close))))

  (close [this]
    (player/pause this))

  (set-volume [_this level]
    (.setVolume volume-ctrl level)))

(defn pcm-stream->playable
  "Create a Playable from a config map and a PCM stream"
  ([config, ^js stream] (pcm-stream->playable (EventEmitter.) config stream))
  ([^EventEmitter events, {:keys [sample-rate channels]}, ^js stream]
   (let [on-error (fn on-error [_e]
                    ; TODO log?
                    )
         create-speaker #(doto (Speaker.
                                 #js {:sampleRate sample-rate
                                      :channels channels
                                      :bitDepth 16})
                           (.on "error" on-error))
         volume-ctrl (prism/VolumeTransformer. #js {:type "s16le"})]

     (doto stream
       (.on "end" #(.emit events "end"))
       (.on "error" on-error))

     (->PcmStreamPlayable
       (atom {:create-speaker create-speaker})
       events
       stream volume-ctrl))))
