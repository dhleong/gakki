(ns gakki.player.promised
  (:require [promesa.core :as p]
            [gakki.player.core :as player :refer [IPlayable]]
            [gakki.player.pcm :refer [pcm-stream->playable]]))

(deftype PromisedStreamPlayable [state]
  IPlayable
  (play [this]
    (swap! state assoc :playing? true)
    (when-let [delegate (:playable @state)]
      (player/play delegate)))

  (pause [this]
    (swap! state assoc :playing? false)
    (when-let [delegate (:playable @state)]
      (player/pause delegate)))

  (close [this]
    (swap! state assoc :closed? true)
    (when-let [delegate (:playable @state)]
      (player/close delegate)))

  (set-volume [this level]
    (swap! state assoc :volume-level level)
    (when-let [delegate (:playable @state)]
      (player/set-volume delegate level)))
  )

(defn promise->playable
  "Create a Playable from a promise which should resolve to:
   {:stream, :config {:sample-rate, :channels}}"
  [pending]
  (let [state (atom nil)]
    (p/let [{:keys [config stream]} pending]
      (when-not (:closed? @state)
        (let [playable (pcm-stream->playable config stream)]
          (swap! state assoc :playable playable)

          ; Transfer enqueued state:
          (when-let [volume-level (:volume-level @state)]
            (player/set-volume playable volume-level))

          (when (:playing? @state)
            (player/play playable)))))

    (->PromisedStreamPlayable state)))
