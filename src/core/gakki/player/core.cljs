(ns gakki.player.core
  (:require ["events" :refer [EventEmitter]]))

(defprotocol IPlayable
  (^EventEmitter
    events [this]
    "Retrieves an EventEmitter that can be used to subscribe to state changes:
     - `end`: Emitted when the playable has finished playback
     - `ending`: Emitted when the playable is nearing the `end` event")
  (play [this])
  (pause [this])
  (set-volume [this level])
  (close [this]))
