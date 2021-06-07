(ns gakki.accounts.ytm.util
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [gakki.accounts.ytm.consts :refer [ytm-kinds]]))

(defn ->seconds [s]
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

(defn runs->text [^js runs-container]
  (when-let [runs (j/get runs-container :runs)]
    (->> runs
         (map #(j/get % :text))
         (str/join " "))))

(defn pick-thumbnail
  "Given some nested object structure like:

     {:thumbnail
      {:croppedSquareThumbnailRenderer
       {:thumbnail
        {:thumbnails
         [{:url \"URL\"}]}}}}

   Extracts a thumbnail URL."
  [^js thumbnail-container]
  (loop [container thumbnail-container]
    (cond
      (nil? container)
      nil

      (js/Array.isArray container)
      (j/get (first container) :url)

      (j/get container :thumbnail)
      (recur (j/get container :thumbnail))

      :else
      (let [container-keys (js/Object.keys container)]
        (when (= 1 (count container-keys))
          (recur (j/get container (first container-keys))))))))

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

