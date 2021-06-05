(ns gakki.player.pcm.promised
  (:require [promesa.core :as p]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]))

(defn- await-storing [destination-atom p]
  (p/then p (fn [v]
              (reset! destination-atom v)
              v)))

(deftype PromisedPCMSource [the-promise resolved-value]
  Object
  (toString [_this]
    (str "PromisedPCM(" (or @resolved-value "pending...") ")"))

  IPrintWithWriter
  (-pr-writer [this writer _]
    (-write writer (.toString this)))

  IPCMSource
  (seekable-duration [_this]
    (p/let [actual (await-storing resolved-value the-promise)]
      (pcm/seekable-duration actual)))

  (prepare [_this] the-promise)

  (read-config [_this]
    (p/let [actual (await-storing resolved-value the-promise)]
      (pcm/read-config actual)))

  (duration-to-bytes [_this duration-seconds]
    (p/let [actual (await-storing resolved-value the-promise)]
      (pcm/duration-to-bytes actual duration-seconds)))

  (open-read-stream [_this]
    (p/let [actual (await-storing resolved-value the-promise)]
      (pcm/open-read-stream actual))))

(defn create
  "Create an IPCMSource instance that delegates to whatever IPCMSource
   the provided promise resolves to."
  [the-promise]
  (->PromisedPCMSource the-promise (atom nil)))
