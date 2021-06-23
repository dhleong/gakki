(ns gakki.accounts.ytm.playback
  (:refer-clojure :exclude [load])
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytdl-core" :as ytdl]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request
                                               generateBody generate-body}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.const :as const]
            [gakki.util.convert :refer [->float ->int]]))


; ======= Talking to YTM directly =========================

(def ^:private re-mime-type #"audio/([^;]+); codecs=\"([a-z0-9]+)")

(defn parse-audio-format [json]
  (when-let [[_ container codec] (re-find re-mime-type (j/get json :mimeType))]
    (when-let [url (j/get json :url)]
      {:config {:container container
                :codec codec

                :frame-size const/default-frame-size
                :duration (->int (j/get json :approxDurationMs))
                :loudness-db (->float (j/get json :loudnessDb))
                :average-bitrate (->int (j/get json :averageBitrate))
                :sample-rate (->int (j/get json :audioSampleRate))
                :channels (->int (j/get json :audioChannels))}
       :url url})))

(defn- load-ytm [cookies id]
  (p/let [body (-> (generate-body #js {})
                   (j/assoc! :videoId id))
          response (send-request cookies
                                 (j/lit
                                   {:endpoint "player"
                                    :body body}))
          formats (j/get-in response [:streamingData :adaptiveFormats])]
    (->> formats
         (keep parse-audio-format)
         (sort-by (comp :average-bitrate :config) >)
         first)))


; ======= ytdl-core =======================================

(defn- load-ytdl-core
  "This fn uses ytdl-core to load the audio format as if it were a youtube
   video, which is convenient and effective for YTM-provided tracks.
   We may be able to get rid of this dependency in the future..."
  [cookies id]
  (p/let [options (j/lit {:requestOptions
                          {:headers {:cookie cookies}}})
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

; ======= Public interface ================================

(defn load [^YTMusic client, id]
  (let [cookies (when client
                  (.-cookie client))
        errors (atom nil)
        catch-errors (fn [p tag]
                       (p/catch p (fn [e]
                                    (swap! errors assoc tag e)
                                    nil)))]
    ; NOTE: Currently we request from both ytdl-core and ytm directly
    ; *in parallel* for expediency. It may be possible extract an URL from YTM
    ; responses that don't explicitly include an URL (which we only seem to get
    ; from uploaded tracks) but it's quite tricky. We would probably have to
    ; reuse some parts of ytdl-core, but would need to submit a PR to refactor
    ; some of ytdl-core to make it usable where we need it....
    (p/plet [from-ytm (-> (load-ytm cookies id)
                          (catch-errors :ytm))
             from-ytdl (-> (load-ytdl-core cookies id)
                           (catch-errors :ytdl))]
      (or from-ytm
          from-ytdl
          (throw (ex-info "Failed to load URL for " {:ytm-id id
                                                     :errors @errors}))))))

#_:clj-kondo/ignore
(comment

  (-> (p/let [client (gakki.accounts.ytm.creds/account->client
                       @(re-frame.core/subscribe [:account :ytm]))
              result (load client "-r-Pq3PnWSs")]
        (cljs.pprint/pprint result))
      (p/catch log/error))

  )
