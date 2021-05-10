(ns gakki.player.promised
  (:require ["events" :refer [EventEmitter]]
            [gakki.player.decode :refer [decode-stream]]
            [promesa.core :as p]
            [gakki.player.core :as player :refer [IPlayable]]
            [gakki.player.pcm :refer [pcm-stream->playable]]))

(deftype PromisedStreamPlayable [state, events]
  IPlayable
  (events [_this] events)

  (play [_this]
    (swap! state assoc :playing? true)
    (when-let [delegate (:playable @state)]
      (player/play delegate)))

  (pause [_this]
    (swap! state assoc :playing? false)
    (when-let [delegate (:playable @state)]
      (player/pause delegate)))

  (close [_this]
    (swap! state assoc :closed? true)
    (when-let [delegate (:playable @state)]
      (player/close delegate)))

  (set-volume [_this level]
    (swap! state assoc :volume-level level)
    (when-let [delegate (:playable @state)]
      (player/set-volume delegate level)))
  )

(defn promise->playable
  "Create a Playable from a promise which should resolve to:

     {:stream, :config}

   See gakki.player.decode/decode-stream for the :config map format"
  ([pending]
   (let [state (atom nil)
         events (EventEmitter.)]
     (p/let [{:keys [config stream]} pending]
       (when-not (:closed? @state)
         (let [playable (pcm-stream->playable
                          events
                          config
                          (decode-stream config stream))]
           (swap! state assoc :playable playable)

           ; Transfer enqueued state:
           (when-let [volume-level (:volume-level @state)]
             (player/set-volume playable volume-level))

           (when (:playing? @state)
             (player/play playable)))))

     (->PromisedStreamPlayable state events))))
