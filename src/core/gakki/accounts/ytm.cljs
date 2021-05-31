(ns gakki.accounts.ytm
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            [clojure.string :as str]
            [gakki.util.logging :as log]
            [promesa.core :as p]
            ["youtubish/dist/creds" :refer [cached OauthCredentialsManager]]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            [gakki.accounts.core :refer [IAccountProvider]]
            [gakki.accounts.ytm.consts :refer [ytm-kinds]]
            [gakki.accounts.ytm.album :as album]
            [gakki.accounts.ytm.artist :as artist]
            [gakki.player.ytm :refer [youtube-id->playable]]))

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

(defn- ->thumbnail [obj]
  (when (js/Array.isArray obj)
    (when-let [entry (first obj)]
      (j/get entry :url))))

(defn- ->seconds [s]
  (let [parts (->> (str/split s ":")
                   (map #(js/parseInt % 10)))
        [h m s] (case (count parts)
                  0 [0 0 0]
                  1 [0 0 (first parts)]
                  2 (cons 0 parts)
                  3 parts)]
    (+ (* h 3600)
       (* m 60)
       s)))

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

(j/defn ^:private ->playlist-item [^:js {:keys [id duration thumbnail title
                                                author album]}]
  {:id id
   :provider :ytm
   :kind :track  ; an assumption...
   :duration (->seconds duration)
   :image-url (->thumbnail thumbnail)
   :title (->text title)
   :artist (->text author)
   :album (->text album)})

(defn- do-resolve-playlist [account playlist-id]
  (p/let [^YTMusic ytm (account->client account)
          data (.getPlaylist ytm playlist-id)]
    ; TODO lazily continue loading the playlist? We can use:
    ;   (>evt [:player/on-resolved :playlist result])
    ; to replace the resolved playlist; if we concat new items with old,
    ; it should "just work"
    {:id (j/get data :playlistId)
     :provider :ytm
     :kind :playlist
     :title (->text (j/get data :title))
     :image-url (->thumbnail (j/get data :thumbnail))
     :items
     (->> (j/get data :content)
          (map ->playlist-item))}))

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
          ;;            (.getPlaylist "MPREb_6zoi6tZGf72"))
          result (send-request (.-cookie client)
                               #js {:id "MPREb_6zoi6tZGf72"
                                    :type "ALBUM"
                                    :endpoint "browse"})
          ]
    (js/console.log (js/JSON.stringify result nil 2))
    )

  (p/let [result (do-resolve-album
                   (:ytm @(re-frame.core/subscribe [:accounts]))
                   "MPREb_XSoe2FaWnVW") ]
    (prn result)
    )

  (p/let [result (do-resolve-artist
                   (:ytm @(re-frame.core/subscribe [:accounts]))
                   "UCvInFYiyeAJOGEjhqJnyaMA") ]
    (prn result)
    (prn (select-keys result [:title :description :radio :shuffle]))
    )

  )

