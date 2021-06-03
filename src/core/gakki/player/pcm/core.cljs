(ns gakki.player.pcm.core)

(defprotocol IPCMSource
  (seekable-duration [this])
  (read-config [this])
  (duration-to-bytes [this duration-seconds])
  (open-read-stream [this]))

(defn duration-to-bytes-with [config duration-seconds]
  ; FIXME: sample size *probably* shouldn't be a const...
  ;  but I'm unsure how to determine it.
  (let [sample-size-bytes 2  ; 16 bit signed integers
        over-precise-bytes (* duration-seconds
                                  (:channels config)
                                  (:sample-rate config)
                                  sample-size-bytes)]
    (int (js/Math.floor over-precise-bytes))))
