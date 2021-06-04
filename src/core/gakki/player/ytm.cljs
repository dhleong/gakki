(ns gakki.player.ytm
  (:require [applied-science.js-interop :as j]
            ["node-fetch" :as fetch]
            ["ytdl-core" :as ytdl]
            [promesa.core :as p]
            [gakki.util.convert :refer [->int]]
            [gakki.player.core :as gp]
            [gakki.player.caching :refer [caching]]
            [gakki.player.promised :refer [promise->playable]]))

(def ^:private cache? true)

(defn- youtube-id->url [id]
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

          ]

    {:config config
     :url (j/get fmt :url)}))

(defn youtube-id->stream [id]
  (p/let [{:keys [config url]} (youtube-id->url id)
          response (fetch url)
          ^js stream (j/get response :body)]
    {:config config
     :stream stream}))

(defn youtube-id->playable [id]
  (promise->playable
    (if cache?
      (caching
        (str "ytm." id)
        #(youtube-id->stream id))

      (p/let [{path :url :as obj} (youtube-id->url id)]
        (assoc obj :path path)))))

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

  (def playable
    (doto (youtube-id->playable "8FV4gcs-MNA")
      (gp/set-volume 0.25)
      (gp/play)))

  (gp/set-volume playable 0.05)
  (gp/play playable)
  (gp/pause playable)
  )


