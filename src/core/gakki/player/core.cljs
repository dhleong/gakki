(ns gakki.player.core)

(defprotocol IPlayable
  (play [this])
  (pause [this])
  (set-volume [this level])
  (close [this]))
