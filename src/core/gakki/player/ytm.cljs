(ns gakki.player.ytm
  (:require [applied-science.js-interop :as j]
            ["node-fetch" :as fetch]
            ["prism-media" :as prism]
            ["speaker" :as Speaker]
            ["ytdl-core" :as ytdl]
            [promesa.core :as p]
            [gakki.util.convert :refer [->int]]
            [gakki.player.promised :refer [promise->playable]]))

(defn- youtube-id->stream [id]
  (p/let [info (ytdl/getInfo id)
          fmt (ytdl/chooseFormat
                (j/get info :formats)
                #js {:quality "highestaudio"})

          ; NOTE: You'd think these would be integers, but... they
          ; might not be.
          config {:sample-rate (->int (j/get fmt :audioSampleRate))
                  :channels (->int (j/get fmt :audioChannels))}

          response (fetch (j/get fmt :url))
          ^js stream (j/get response :body)]

    {:config config
     :stream (-> stream
                 (.pipe (prism/opus.WebmDemuxer.))
                 (.pipe (prism/opus.Decoder.
                          #js {:rate (:sample-rate config)
                               :channels (:channels config)
                               :frameSize 960})))}))

(defn youtube-id->playable [id]
  (promise->playable
    (youtube-id->stream id)))

(comment
  (def playable
    (youtube-id->playable "8FV4gcs-MNA")))
