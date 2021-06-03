(ns gakki.player.pcm.caching
  (:require [promesa.core :as p]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.decode :refer [decode-stream]]))

(deftype CachingPCMSource [^Readable stream, config, ^String disk-path, state]
  IPCMSource
  (seekable-duration [this]
    (p/let [analysis (pcm/read-config this)]
      (:duration analysis)))

  (read-config [_this] config)

  (duration-to-bytes [this duration-seconds]
    (p/let [config (pcm/read-config this)]
      (pcm/duration-to-bytes-with config duration-seconds)))

  (open-read-stream [_this]
    ; TODO
    (decode-stream config stream)))

(defn create [^Readable stream, config destination-path]
  ; TODO
  (->CachingPCMSource stream config, destination-path (atom nil)))
