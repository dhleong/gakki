(ns gakki.player.pcm.disk
  (:require ["fs" :rename {createReadStream create-read-stream}]
            [promesa.core :as p]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.analyze :refer [analyze-audio]]
            [gakki.player.decode :refer [decode-stream]]))

(defn- analyze-caching [cache-atom atom-key path]
  (if-let [cached (get @cache-atom atom-key)]
    (p/resolved cached)

    (p/let [analysis (analyze-audio path)]
      (swap! cache-atom assoc atom-key analysis)
      analysis)))

(defn- open-stream [path]
  (p/create
    (fn [p-resolve p-reject]
      (let [s (create-read-stream path)]
        (doto s
          (.on "error" p-reject)
          (.on "open" #(p-resolve s)))))))

(deftype DiskPCMSource [^String path, state]
  IPCMSource
  (seekable-duration [this]
    (p/let [analysis (pcm/read-config this)]
      (:duration analysis)))

  (read-config [_this]
    (analyze-caching state :analysis path))

  (duration-to-bytes [this duration-seconds]
    (p/let [config (pcm/read-config this)]
      (pcm/duration-to-bytes-with config duration-seconds)))

  (open-read-stream [this]
    (p/let [config (pcm/read-config this)
            encoded-stream (open-stream path)]
      (decode-stream config encoded-stream))))

(defn create [path]
  (->DiskPCMSource path (atom nil)))
