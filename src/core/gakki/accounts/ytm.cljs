(ns gakki.accounts.ytm
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            [clojure.string :as str]
            [promesa.core :as p]
            ["youtubish/dist/creds" :refer [cached OauthCredentialsManager]]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            [gakki.accounts.core :refer [IAccountProvider]]
            [gakki.accounts.ytm.consts :refer [ytm-kinds]]
            [gakki.accounts.ytm.album :as album]
            [gakki.accounts.ytm.artist :as artist]
            [gakki.accounts.ytm.playlist :as playlist]
            [gakki.player.ytm :refer [youtube-id->playable]]
            [gakki.util.logging :as log]))

(defonce ^:private account->creds
  (memoize
    (fn [account]
      (cached
        (OauthCredentialsManager.
          (clj->js account)
          #js {:persistCredentials
               (fn [creds]
                 (let [updated (merge account
                                      (js->clj creds :keywordize-keys true))]
                   (>evt [:auth/save :ytm updated {:load-home? false}])))})))))

(defonce ^:private created-creds (atom nil))

(defn- ->text [obj]
  (or (when (string? obj)
        obj)
      (when-let [text (j/get obj :text)]
        text)

      (when (js/Array.isArray obj)
        (->> obj
             (map ->text)
             (str/join "; ")))

      (str obj)))

(j/defn ^:private ->home-item [^:js {:keys [title navigationEndpoint]}]
  {:title (->text title)
   :provider :ytm
   :id (or (j/get-in navigationEndpoint [:watchEndpoint :videoId])
           (j/get-in navigationEndpoint [:browseEndpoint :browseId]))
   :kind (or (when (j/get-in navigationEndpoint [:watchEndpoint :videoId])
               :track)

             (when-let [ytm-kind (j/get-in navigationEndpoint
                                           [:browseEndpoint
                                            :browseEndpointContextSupportedConfigs
                                            :browseEndpointContextMusicConfig
                                            :pageType])]
               (get ytm-kinds ytm-kind (keyword "unknown"
                                                ytm-kind))))})

(defn- account->client [account]
  (p/let [initial? (nil? (get @created-creds account))
          start (js/Date.now)
          creds (account->creds account)
          cookies-obj (.get creds)
          delta (- (js/Date.now) start)]

    ; logging:
    (swap! created-creds assoc account true)
    (if initial?
      (log/timing :ytm/initial-cookie-fetch delta)
      (log/timing :ytm/cookie-refresh delta))

    (YTMusic. (j/get cookies-obj :cookies))))

(defn- do-fetch-home [account]
  (log/with-timing-promise :ytm/parse-and-fetch-home
    (p/let [^YTMusic ytm (account->client account)

            start (js/Date.now)
            home (.getHomePage ytm)]
      (log/timing :ytm/fetch-home (- (js/Date.now) start))

      {:categories
       (->> (j/get home :content)
            (map (j/fn [^:js {:keys [title content]}]
                   {:title title
                    :items (map ->home-item content)})))})))

(defn- do-resolve-playlist [account playlist-id]
  (p/let [^YTMusic ytm (account->client account)]
    ; TODO lazily continue loading the playlist? We can use:
    ;   (>evt [:player/on-resolved :playlist result])
    ; to replace the resolved playlist; if we concat new items with old,
    ; it should "just work"
    (playlist/load ytm playlist-id)))

(defn- do-resolve-album [account album-id]
  (p/let [^YTMusic ytm (account->client account)]
    (album/load ytm album-id)))

(defn- do-resolve-artist [account artist-id]
  (p/let [^YTMusic ytm (account->client account)]
    (artist/load ytm artist-id)))

(deftype YTMAccountProvider []
  IAccountProvider
  (get-name [_this] "YouTube Music")
  (describe-account [_ account]
    (when-let [email (-> account :user :email)]
      (str email)))

  (create-playable
    [_this info]
    (youtube-id->playable (:id info)))

  (fetch-home [_ account]
    ; NOTE: this is pulled out to a separate fn to facilitate hot-reload dev
    (do-fetch-home account))

  (resolve-album [_ account album-id]
    (do-resolve-album account album-id))

  (resolve-artist [_ account artist-id]
    (do-resolve-artist account artist-id))

  (resolve-playlist [_ account playlist-id]
    (do-resolve-playlist account playlist-id))
  )

#_:clj-kondo/ignore
(comment
  (p/let [client (account->client
                   (:ytm @(re-frame.core/subscribe [:accounts])))
          result (-> client
                     (.getArtist "UCvInFYiyeAJOGEjhqJnyaMA"))]
    (println result))

  (p/let [client (account->client
                   (:ytm @(re-frame.core/subscribe [:accounts])))
          ;; result (-> client
          ;;            (.getPlaylist "VLPLw6X_oq5Z8kl_Myg9QL1ZKxV1BobTeXrb"))
          result (send-request (.-cookie client)
                               #js {:id "VLPLw6X_oq5Z8kl_Myg9QL1ZKxV1BobTeXrb"
                                    :type "ALBUM"
                                    :endpoint "browse"})
          ]
    (println (js/JSON.stringify result nil 2))
    )

  (p/let [result (do-resolve-playlist
                   (:ytm @(re-frame.core/subscribe [:accounts]))
                   "VLPLw6X_oq5Z8kl_Myg9QL1ZKxV1BobTeXrb") ]
    (cljs.pprint/pprint result))

  (p/let [result (do-resolve-album
                   (:ytm @(re-frame.core/subscribe [:accounts]))
                   "MPREb_XSoe2FaWnVW") ]
    (prn result))

  (p/let [result (do-resolve-artist
                   (:ytm @(re-frame.core/subscribe [:accounts]))
                   "UCvInFYiyeAJOGEjhqJnyaMA") ]
    (prn result)
    (prn (select-keys result [:title :description :radio :shuffle]))
    )

  )

