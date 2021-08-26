(ns gakki.accounts.ytm
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [promesa.core :as p]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            [gakki.accounts.core :refer [IAccountProvider]]
            [gakki.accounts.ytm.creds :refer [account->client]]
            [gakki.accounts.ytm.consts :refer [ytm-kinds]]
            [gakki.accounts.ytm.album :as album]
            [gakki.accounts.ytm.artist :as artist]
            [gakki.accounts.ytm.playable :as playable]
            [gakki.accounts.ytm.playlist :as playlist]
            [gakki.accounts.ytm.search :as search]
            [gakki.accounts.ytm.search-suggest :as search-suggest]
            [gakki.accounts.ytm.upnext :as upnext]
            [gakki.accounts.ytm.util :as util]
            [gakki.util.logging :as log]))


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

(j/defn ^:private ->home-item [^:js {:keys [title navigationEndpoint] :as item}]
  {:title (->text title)
   :provider :ytm
   :id (or (j/get-in navigationEndpoint [:watchEndpoint :videoId])
           (j/get-in navigationEndpoint [:browseEndpoint :browseId]))
   :image-url (util/pick-thumbnail item)
   :kind (or (when (j/get-in navigationEndpoint [:watchEndpoint :videoId])
               :track)

             (when-let [ytm-kind (j/get-in navigationEndpoint
                                           [:browseEndpoint
                                            :browseEndpointContextSupportedConfigs
                                            :browseEndpointContextMusicConfig
                                            :pageType])]
               (get ytm-kinds ytm-kind (keyword "unknown"
                                                ytm-kind))))})


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

(defn- do-paginate [account entity index]
  (when-let [continuations (first (:continuations entity))]
    ((log/of :ytm) "Paginate" (:kind entity) (:id entity) "@" continuations "...")
    (p/let [^YTMusic ytm (account->client account)
            up-next (upnext/load
                      ytm
                      (assoc entity
                             :id (if (= :track (:radio/kind entity))
                                   (get-in entity [:items index :id])
                                   (:id entity))
                             :continuation (j/get-in
                                             continuations
                                             [:nextRadioContinuationData
                                              :continuation])
                             :click-tracking-params (j/get-in
                                                      continuations
                                                      [:nextRadioContinuationData
                                                       :clickTrackingParams])
                             :index index))]

      {:entity (-> entity
                   (update :items into (:items up-next))
                   (assoc :continuations (:continuations up-next)))
       :new-items (:items up-next)})))

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

(defn- do-resolve-radio [account radio]
  (p/let [^YTMusic ytm (account->client account)]
    ; TODO lazily continue loading the playlist?
    (upnext/load ytm radio)))

(deftype YTMAccountProvider []
  IAccountProvider
  (get-name [_this] "YouTube Music")
  (describe-account [_ account]
    (when-let [email (-> account :user :email)]
      (str email)))

  (create-playable [_this account info]
    (playable/from-id account (:id info)))

  (fetch-home [_ account]
    ; NOTE: this is pulled out to a separate fn to facilitate hot-reload dev
    (do-fetch-home account))

  (paginate [_ account entity index]
    (do-paginate account entity index))

  (resolve-album [_ account {album-id :id}]
    (do-resolve-album account album-id))

  (resolve-artist [_ account {artist-id :id}]
    (do-resolve-artist account artist-id))

  (resolve-playlist [_ account {playlist-id :id}]
    (do-resolve-playlist account playlist-id))

  (resolve-radio [_ account radio]
    (do-resolve-radio account radio))

  (search [_ account query]
    (p/let [^YTMusic ytm (account->client account)]
      (search/perform ytm query)))

  (search-suggest [_ account partial-query]
    (p/let [^YTMusic ytm (account->client account)]
      (search-suggest/load ytm partial-query))))

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

  ; this is a "shuffle CHVRCHES" radio
  (-> (p/let [result (do-resolve-radio
                       (:ytm @(re-frame.core/subscribe [:accounts]))
                       {:id "80QNzlx-Fyg"
                        :playlist-id "RDAOoxcn6rFh4zxhtR0lDvIPBA"
                        :radio/kind :track})]
        (cljs.pprint/pprint result))
      (p/catch log/error))

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

