(ns gakki.player.pcm.disk
  (:require ["fs" :rename {createReadStream create-read-stream}]
            ["fs/promises" :as fs]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.analyze :as analyze]
            [gakki.player.decode :refer [decode-stream]]))

(defn- open-stream [path]
  (p/create
    (fn [p-resolve p-reject]
      (let [s (create-read-stream path)]
        (doto s
          (.on "error" p-reject)
          (.on "open" #(p-resolve s)))))))

(deftype DiskPCMSource [^String path, state]
  Object
  (toString [_this]
    (str "DiskPCM(" (path/basename path) ")"))

  IPrintWithWriter
  (-pr-writer [this writer _]
    (-write writer (.toString this)))

  IPCMSource
  (seekable-duration [this]
    (p/let [analysis (pcm/read-config this)]
      (:duration analysis)))

  (prepare [_this]
    (fs/access path))

  (read-config [_this]
    (analyze/audio-caching state :analysis path))

  (duration-to-bytes [this duration-seconds]
    (p/let [config (pcm/read-config this)]
      (pcm/duration-to-bytes-with config duration-seconds)))

  (open-read-stream [this]
    (p/plet [config (pcm/read-config this)
             encoded-stream (open-stream path)]
      (decode-stream config encoded-stream))))

(defn create [path]
  (->DiskPCMSource path (atom nil)))
