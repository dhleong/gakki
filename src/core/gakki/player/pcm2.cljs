(ns gakki.player.pcm2
  (:require ["fs" :rename {createReadStream create-read-stream}]
            [promesa.core :as p]
            [gakki.player.analyze :refer [analyze-audio]]
            [gakki.player.decode :refer [decode-stream]]))

(defprotocol IPCMSource
  (seekable-duration [this])
  (read-config [this])
  (duration-to-bytes [this duration-seconds])
  (open-read-stream [this]))

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
    (p/let [analysis (read-config this)]
      (:duration analysis)))

  (read-config [_this]
    (analyze-caching state :analysis path))

  (duration-to-bytes [this duration-seconds]
    ; FIXME: sample size *probably* shouldn't be a const...
    ;  but I'm unsure how to determine it.
    (p/let [sample-size-bytes 2  ; 16 bit signed integers
            config (read-config this)]
      (* duration-seconds
         (:channels config)
         (:sample-rate config)
         sample-size-bytes)))

  (open-read-stream [this]
    (p/let [config (read-config this)
            encoded-stream (open-stream path)]
      (decode-stream config encoded-stream))))

(defn create-disk-source [path]
  (->DiskPCMSource path (atom nil)))
