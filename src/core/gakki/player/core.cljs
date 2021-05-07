(ns gakki.player.core)

(defprotocol IPlayable
  (stop [this])
  (set-volume [this level]))
