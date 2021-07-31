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

(defn runs->text
  ([^js runs-container] (runs->text runs-container " "))
  ([^js runs-container separator]
   (when-let [runs (j/get runs-container :runs)]
     (->> runs
          (map #(j/get % :text))
          (str/join separator)))))

(defn single-key-child [^js obj]
  (let [container-keys (js/Object.keys obj)]
    (when (= 1 (count container-keys))
      (j/get obj (first container-keys)))) )

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
      (when-let [child (single-key-child container)]
        (recur child)))))

(defn split-string-by-dots [s]
  (str/split s #"  •  "))

(defn split-runs-by-dots [runs]
  (-> (runs->text runs)
      (split-string-by-dots)))

(defn unpack-navigation-endpoint [^js runs-container-or-endpoint]
  (let [endpoint (or (j/get runs-container-or-endpoint :navigationEndpoint)
                     (j/get-in runs-container-or-endpoint [:runs 0 :navigationEndpoint]))
        playlist-id (j/get-in endpoint [:watchPlaylistEndpoint :playlistId])
        watch-id (j/get-in endpoint [:watchEndpoint :videoId])
        id (or watch-id
               playlist-id
               (j/get-in endpoint [:browseEndpoint :browseId]))]
    (when id
      {:id id
       :playlist-id (or playlist-id
                        (j/get-in endpoint [:watchEndpoint :playlistId]))
       :params (or (j/get-in endpoint [:watchEndpoint :params])
                   (j/get-in endpoint [:watchPlaylistEndpoint :params]))
       :provider :ytm
       :kind (let [raw-kind (j/get-in endpoint [:browseEndpoint
                                                :browseEndpointContextSupportedConfigs
                                                :browseEndpointContextMusicConfig
                                                :pageType])]
               (get ytm-kinds raw-kind (cond
                                         playlist-id :playlist
                                         watch-id :track
                                         :else :unknown)))})))

