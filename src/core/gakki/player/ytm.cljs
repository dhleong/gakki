(ns gakki.player.ytm
  (:require [applied-science.js-interop :as j]
            ["node-fetch" :as fetch]
            ["ytdl-core" :as ytdl]
            [promesa.core :as p]
            [gakki.util.convert :refer [->int]]
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
                  :sample-rate (->int (j/get fmt :audioSampleRate))
                  :channels (->int (j/get fmt :audioChannels))}

          response (fetch (j/get fmt :url))
          ^js stream (j/get response :body)]

    {:config config
     :stream stream}))

(defn youtube-id->playable [id]
  (promise->playable
    (youtube-id->stream id)))

(comment
  (p/let [id "8FV4gcs-MNA"
          info (ytdl/getInfo id)
          fmt (ytdl/chooseFormat
                (j/get info :formats)
                #js {:quality "highestaudio"})]
    (println fmt))

  (def playable
    (doto (youtube-id->playable "8FV4gcs-MNA")
      (gakki.player.core/set-volume 0.5)
      (gakki.player.core/play))))
