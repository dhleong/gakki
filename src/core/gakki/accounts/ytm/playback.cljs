(ns gakki.accounts.ytm.playback
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
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

; ======= Public interface ================================

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
