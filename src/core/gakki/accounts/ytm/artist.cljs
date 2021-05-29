(ns gakki.accounts.ytm.artist
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
            [gakki.accounts.ytm.util :refer [runs->text]]))

(defn load [^YTMusic client id]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id id
                                      :type "ARTIST"
                                      :endpoint "browse"})
          raw-rows (-> response
                       (j/get-in [:contents
                                  :singleColumnBrowseResultsRenderer
                                  :tabs
                                  0
                                  :tabRenderer
                                  :content
                                  :sectionListRenderer
                                  :contents]))]
    (println "response=" response)
    {:id id
     :kind :artist
     :provider :ytm
     :title (-> response
                (j/get-in [:header :musicImmersiveHeaderRenderer :title])
                (runs->text))
     :items (keep music-shelf->section raw-rows)}))
