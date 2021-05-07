(ns gakki.player.promised
  (:require [promesa.core :as p]
            [gakki.player.core :as player :refer [IPlayable]]
            [gakki.player.pcm :refer [pcm-stream->playable]]))

(deftype PromisedStreamPlayable [state]
  IPlayable
  (stop [this]
    (swap! state assoc :stopped? true)
    (when-let [delegate (:playable @state)]
      (player/stop delegate)))

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
      (when-not (:stopped? @state)
        (let [playable (pcm-stream->playable config stream)]
          (swap! state assoc :playable playable)
          (when-let [volume-level (:volume-level @state)]
            (player/set-volume playable volume-level)))))
    (->PromisedStreamPlayable state)))
