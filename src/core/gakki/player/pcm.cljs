(ns gakki.player.pcm
  (:require ["speaker" :as Speaker]
            ["pcm-volume" :as VolumeCtrl]
            [gakki.player.core :refer [IPlayable]]))

(deftype PcmStreamPlayable [^js stream, ^js speaker, ^js volume-ctrl]
  IPlayable
  (stop [this]
    (doto stream
      (.unpipe volume-ctrl))
    (doto volume-ctrl
      (.unpipe speaker))

    (doto speaker
      (.close false)))

  (set-volume [this level]
    (.setVolume volume-ctrl level)))

(defn pcm-stream->playable
  "Create a Playable from a config map and a PCM stream"
  [{:keys [sample-rate channels]}, ^js stream]
  (let [speaker (Speaker.
                  #js {:sampleRate sample-rate
                       :channels channels
                       :bitDepth 16})
        volume-ctrl (VolumeCtrl.)
        on-error (fn on-error [e]
                   ; TODO log?
                   )]

    (.on speaker "error" on-error)

    (-> stream
        (.pipe volume-ctrl)
        (.pipe speaker)
        (.on "error" on-error))
    (->PcmStreamPlayable stream speaker volume-ctrl)))
