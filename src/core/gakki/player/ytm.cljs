(ns gakki.player.ytm
  (:require [applied-science.js-interop :as j]
            ["fs" :as fs]
            ["node-fetch" :as fetch]
            ["ytdl-core" :as ytdl]
            [promesa.core :as p]
            [gakki.util.convert :refer [->int]]
            [gakki.player.caching :refer [caching]]
            [gakki.player.promised :refer [promise->playable]]))

(defn- youtube-id->stream [id]
  (p/let [info (ytdl/getInfo id)
          fmt (ytdl/chooseFormat
                (j/get info :formats)
                #js {:quality "highestaudio"})

          config {:container (j/get fmt :container)
                  :codec (j/get fmt :audioCodec)

                  ; NOTE: You'd think these would be integers, but...
                  ; they might not be.
                  :duration (->int (j/get fmt :approxDurationMs))
                  :loudness-db (->int (j/get fmt :loudnessDb))
                  :sample-rate (->int (j/get fmt :audioSampleRate))
                  :channels (->int (j/get fmt :audioChannels))}

          response (fetch (j/get fmt :url))
          ^js stream (j/get response :body)]

    {:config config
     :stream stream}))

(defn youtube-id->playable [id]
  (promise->playable
    (caching
      (str "ytm." id)
      #(youtube-id->stream id))))

(comment
  (p/let [id "8FV4gcs-MNA"
          info (ytdl/getInfo id)
          fmt (ytdl/chooseFormat
                (j/get info :formats)
                #js {:quality "highestaudio"})]
    (println (-> fmt
                 (js->clj :keywordize-keys true)
                 (assoc :url "<url>")
                 (assoc :s "<s>")
                 str)))

  (p/let [{:keys [stream config]} (youtube-id->stream "8FV4gcs-MNA")]
    (println config)
    (-> stream
        (.on "end" (fn []
                     (println "done!")))
        (.on "error" (fn [e]
                       (println "ERR" e)))
        (.pipe (fs/createWriteStream "/Users/daniel/Library/Caches/gakki/ycm.cache"))
        (.on "error" (fn [e]
                       (println "ERR" e)))
        )
    (.resume stream))

  (def playable
    (doto (youtube-id->playable "8FV4gcs-MNA")
      (gakki.player.core/set-volume 0.25)
      (gakki.player.core/play)))

  (gakki.player.core/set-volume playable 0.05)
  (gakki.player.core/play playable)
  (gakki.player.core/pause playable)
  )
