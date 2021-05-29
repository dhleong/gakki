(ns gakki.accounts.ytm.util
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [gakki.accounts.ytm.consts :refer [ytm-kinds]]))

(defn runs->text [^js runs-container]
  (when-let [runs (j/get runs-container :runs)]
    (->> runs
         (map #(j/get % :text))
         (str/join " "))))

(defn unpack-navigation-endpoint [^js runs-container-or-endpoint]
  (let [endpoint (or (j/get runs-container-or-endpoint :navigationEndpoint)
                     (j/get-in runs-container-or-endpoint [:runs 0 :navigationEndpoint]))
        playlist-id (j/get-in endpoint [:watchPlaylistEndpoint :playlistId])
        watch-id (or (j/get-in endpoint [:watchEndpoint :videoId])
                     playlist-id)]
    {:id (or watch-id
             (j/get-in endpoint [:browseEndpoint :browseId]))
     :provider :ytm
     :kind (let [raw-kind (j/get-in endpoint [:browseEndpoint
                                              :browseEndpointContextSupportedConfigs
                                              :browseEndpointContextMusicConfig
                                              :pageType])]
             (get ytm-kinds raw-kind (cond
                                       playlist-id :playlist
                                       watch-id :track
                                       :else :unknown)))}))

