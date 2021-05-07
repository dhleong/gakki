(ns gakki.player.pcm
  (:require ["speaker" :as Speaker]
            ["pcm-volume" :as VolumeCtrl]
            [gakki.player.core :as player :refer [IPlayable]]))

(deftype PcmStreamPlayable [state, ^js stream, ^js volume-ctrl]
  IPlayable
  (play [this]
    (when-not (:speaker @state)
      (let [speaker ((:create-speaker @state))]
        (swap! state assoc :speaker speaker)
        (-> stream
            (.pipe volume-ctrl)
            (.pipe speaker)))))

  (pause [this]
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

  (set-volume [this level]
    (.setVolume volume-ctrl level)))

(defn pcm-stream->playable
  "Create a Playable from a config map and a PCM stream"
  [{:keys [sample-rate channels]}, ^js stream]
  (let [on-error (fn on-error [e]
                   ; TODO log?
                   )
        create-speaker #(doto (Speaker.
                                #js {:sampleRate sample-rate
                                     :channels channels
                                     :bitDepth 16})
                          (.on "error" on-error))
        volume-ctrl (VolumeCtrl.) ]

    (.on stream "error" on-error)

    (->PcmStreamPlayable
      (atom {:create-speaker create-speaker})
      stream volume-ctrl)))
