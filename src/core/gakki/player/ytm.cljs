(ns gakki.player.ytm
  (:require [applied-science.js-interop :as j]
            ["node-fetch" :as fetch]
            ["ytdl-core" :as ytdl]
            [promesa.core :as p]
            [gakki.accounts.ytm.creds :refer [account->cookies]]
            [gakki.util.convert :refer [->int]]
            [gakki.player.core :as gp]
            [gakki.player.pcm :as pcm]
            [gakki.player.track :as track]))

(defn- youtube-id->url [account id]
  (p/let [cookies (when account
                    (account->cookies account))
          options (j/lit {:requestOptions
                          {:headers {:cookie cookies}}})
          _ (println options)
          info (ytdl/getInfo id options)
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
                  :channels (->int (j/get fmt :audioChannels))}]

    {:config config
     :url (j/get fmt :url)}))

(defn youtube-id->stream [account id]
  (p/let [{:keys [config url]} (youtube-id->url account id)
          response (fetch url)
          ^js stream (j/get response :body)]
    {:config config
     :stream stream}))

(defn youtube-id->playable
  ([id] (youtube-id->playable nil id))
  ([account id]
   (track/create-playable
     (pcm/create-caching-source
       (str "ytm." id)
       #(youtube-id->stream account id)))))

#_:clj-kondo/ignore
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

  (p/let [id "fl9rdaEx1gA"
          account @(re-frame.core/subscribe [:account :ytm])
          info (youtube-id->url account id)]
    (println info))

  (def playable
    (doto (youtube-id->playable "8FV4gcs-MNA")
      (gp/set-volume 0.25)
      (gp/play)))

  (gp/set-volume playable 0.05)
  (gp/play playable)
  (gp/pause playable)
  )


