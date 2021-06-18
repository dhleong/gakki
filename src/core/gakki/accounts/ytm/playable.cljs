(ns gakki.accounts.ytm.playable
  (:require [applied-science.js-interop :as j]
            ["node-fetch" :as fetch]
            [promesa.core :as p]
            [gakki.accounts.ytm.creds :refer [account->client]]
            [gakki.accounts.ytm.playback :as playback]
            [gakki.player.pcm :as pcm]
            [gakki.player.track :as track]))

(defn youtube-id->stream [account id]
  (p/let [client (when account
                   (account->client account))
          {:keys [config url]} (playback/load client id)
          response (fetch url)
          ^js stream (j/get response :body)]
    {:config config
     :stream stream}))

(defn from-id
  "Create a Playable from a YouTube (music) video ID.
   `account` *should* be a Google account map for best compatibility, but
   *may* be nil for public (not auth-required) videos."
  [account id]
  (track/create-playable
    id
    (pcm/create-caching-source
      (str "ytm." id)
      #(youtube-id->stream account id))))

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
      (gakki.player.core/set-volume 0.25)
      (gakki.player.core/play)))

  (gakki.player.core/set-volume playable 0.05)
  (gakki.player.core/play playable)
  (gakki.player.core/pause playable)
  )



