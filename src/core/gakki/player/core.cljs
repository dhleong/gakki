(ns gakki.player.core
  (:require ["events" :refer [EventEmitter]]))

(defprotocol IPlayable
  (^EventEmitter
    events [this]
    "Retrieves an EventEmitter that can be used to subscribe to state changes:
     - `end`: Emitted when the playable has finished playback")
  (play [this])
  (pause [this])
  (set-volume [this level])
  (close [this]))
