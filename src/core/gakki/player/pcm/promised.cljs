(ns gakki.player.pcm.promised
  (:require [promesa.core :as p]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]))

(deftype PromisedPCMSource [the-promise]
  IPCMSource
  (seekable-duration [_this]
    (p/let [actual the-promise]
      (pcm/seekable-duration actual)))

  (read-config [_this]
    (p/let [actual the-promise]
      (pcm/read-config actual)))

  (duration-to-bytes [_this duration-seconds]
    (p/let [actual the-promise]
      (pcm/duration-to-bytes actual duration-seconds)))

  (open-read-stream [_this]
    (p/let [actual the-promise]
      (pcm/open-read-stream actual))))

(defn create
  "Create an IPCMSource instance that delegates to whatever IPCMSource
   the provided promise resolves to."
  [the-promise]
  (->PromisedPCMSource the-promise))
