(ns gakki.accounts.ytm
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["youtubish/dist/creds" :refer [cached OauthCredentialsManager]]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.accounts.core :refer [IAccountProvider]]
            [gakki.player.ytm :refer [youtube-id->playable]]))

(def ^:private account->creds
  (memoize
    (fn [account]
      (cached
        (OauthCredentialsManager.
          (clj->js account)
          #js {:persistCredentials
               (fn [_creds]
                 (p/do!
                   (println "TODO: Persist creds")))})))))

(def ^:private ytm-kinds
  {"MUSIC_PAGE_TYPE_ALBUM" :album
   "MUSIC_PAGE_TYPE_ARTIST" :artist
   "MUSIC_PAGE_TYPE_PLAYLIST" :playlist})

(j/defn ^:private ->item [^:js {:keys [title navigationEndpoint]}]
  {:title (or (when (string? title)
                title)
              (when-let [text (j/get title :text)]
                text)
              (str title))
   :provider :ytm
   :id (or (j/get-in navigationEndpoint [:watchEndpoint :videoId])
           (j/get-in navigationEndpoint [:browseEndpoint :browseId]))
   :kind (or (when (j/get-in navigationEndpoint [:watchEndpoint :videoId])
               :song)

             (when-let [ytm-kind (j/get-in navigationEndpoint
                                           [:browseEndpoint
                                            :browseEndpointContextSupportedConfigs
                                            :browseEndpointContextMusicConfig
                                            :pageType])]
               (get ytm-kinds ytm-kind (keyword "unknown"
                                                ytm-kind))))})


(defn- do-fetch-home [account]
  (p/let [creds (account->creds account)
          cookies-obj (.get creds)
          ^js ytm (YTMusic. (j/get cookies-obj :cookies))
          home (.getHomePage ytm)]
    (println home)
    {:categories
     (->> (j/get home :content)
          (map (j/fn [^:js {:keys [title content]}]
                 {:title title
                  :items (map ->item content)})))}))

(deftype YTMAccountProvider []
  IAccountProvider
  (get-name [_this] "YouTube Music")
  (describe-account [_ account]
    (str (-> account :user :email)))

  (create-playable
    [_this info]
    (youtube-id->playable (:id info)))

  (fetch-home [_ account]
    ; NOTE: this is pulled out to a separate fn to facilitate hot-reload dev
    (do-fetch-home account)))
