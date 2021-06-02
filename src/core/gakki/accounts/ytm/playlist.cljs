(ns gakki.accounts.ytm.playlist
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [promesa.core :as p]
            [gakki.accounts.ytm.music-shelf :refer [parse-shelf-item]]
            [gakki.accounts.ytm.util :as util :refer [runs->text]]))

(defn- parse-items [^js response]
  (let [shelf (j/get-in response [:contents
                                  :singleColumnBrowseResultsRenderer
                                  :tabs
                                  0
                                  :tabRenderer
                                  :content
                                  :sectionListRenderer
                                  :contents
                                  0
                                  :musicPlaylistShelfRenderer])]
    ; TODO extract continuation data
    (mapv parse-shelf-item (j/get shelf :contents))))

(defn load [^YTMusic client, id]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id (if-not (str/starts-with? id "VL")
                                            (str "VL" id)
                                            id)
                                      :type "PLAYLIST"
                                      :endpoint "browse"})
          header (or (j/get-in response [:header :musicDetailHeaderRenderer])
                     (j/get-in response [:header
                                         :musicEditablePlaylistDetailHeaderRenderer
                                         :header
                                         :musicDetailHeaderRenderer]))]
    {:id id
     :provider :ytm
     :kind :playlist
     :title (runs->text (j/get header :title))
     :image-url (util/pick-thumbnail header)
     :items (parse-items response)}))
