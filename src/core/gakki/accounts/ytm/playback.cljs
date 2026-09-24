(ns gakki.accounts.ytm.playback
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

(defn- parse-core-config [json]
  {:frame-size const/default-frame-size
   :duration (->int (or (j/get json :approxDurationMs)
                        (j/get json .-approx_duration_ms)))
   :loudness-db (->float (or (j/get json :loudnessDb)
                             (j/get json .-loudness_db)))
   :average-bitrate (->int (or (j/get json :averageBitrate)
                               (j/get json .-average_bitrate)))
   :sample-rate (->int (or (j/get json :audioSampleRate)
                           (j/get json .-audio-sample-rate)))
   :channels (->int (or (j/get json :audioChannels)
                        (j/get json .-audio_channels)))})

(defn- parse-mime-type [mime]
  (when-let [[_ container codec] (re-find re-mime-type mime)]
    {:container container
     :codec codec}))

(defn parse-audio-format [json]
  (when-let [{:keys [container codec]} (parse-mime-type
                                        (or (j/get json :mimeType)
                                            (j/get json .-mime_type)))]
    (when-let [url (j/get json :url)]
      {:config (assoc (parse-core-config json)
                      :container container
                      :codec codec)
       :url url})))

(defn- load-ytm [cookies id]
  (p/let [body (-> (generate-body #js {})
                   (j/assoc! :videoId id))
          response (send-request cookies
                                 (j/lit
                                  {:endpoint "player"
                                   :body body}))
          formats (j/get-in response [:streamingData :adaptiveFormats])]
    (println response)
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

          config (assoc (parse-core-config fmt)
                        :container (j/get fmt :container)
                        :codec (j/get fmt :audioCodec))]

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

(defn- valid-format? [fmt]
  (when (or (j/get fmt .-url)
            (j/get fmt .-signature_cipher)
            (j/get fmt .-cipher))
    fmt))

(defn- choose-format [^js info]
  (some valid-format? [(j/call info .-chooseFormat
                               #js {:type "audio"
                                    :quality "best"})
                       (j/call info .-chooseFormat
                               #js {:type "audio"})
                       (j/call info .-chooseFormat #js {})]))

(defn- parse-innertube [^js client, info]
  (if-let [^js fmt (choose-format info)]
    (p/let [url (j/call fmt .-decipher
                        (j/get-in client [.-session .-player]))]
      (parse-audio-format (j/assoc! fmt :url url)))
    (throw (ex-info "No playable data" {:info info}))))

(defn load-innertube [^js client, id]
  (p/let [info (j/call-in client [.-music .-getInfo] id)]
    (parse-innertube client info)))

#_:clj-kondo/ignore
(comment

  (-> (p/let [client (gakki.accounts.ytm.creds/get-authd-innertube
                      @(re-frame.core/subscribe [:account :ytm]))
              result (load-innertube client "RYu1dAdkSWI")]
        (cljs.pprint/pprint result))
      (p/catch println))

  (-> (p/let [client (gakki.accounts.ytm.creds/account->client
                      @(re-frame.core/subscribe [:account :ytm]))
              result (load client "-r-Pq3PnWSs")]
        (cljs.pprint/pprint result))
      (p/catch log/error)))
