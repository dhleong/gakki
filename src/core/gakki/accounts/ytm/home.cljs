(ns gakki.accounts.ytm.home
  (:refer-clojure :exclude [load])
  (:require [applied-science.js-interop :as j]
            [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]))

(defn inflate [^js response]
  (let [raw-shelves (j/get-in response [:contents
                                        :singleColumnBrowseResultsRenderer
                                        :tabs
                                        0
                                        :tabRenderer
                                        :content
                                        :sectionListRenderer
                                        :contents])]

    ; TODO extract continuation data
    {:categories (->> raw-shelves
                      (keep music-shelf->section)
                      vec)}))

(defn load [^YTMusic client]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id "FEmusic_home"
                                      :endpoint "browse"})]
    (inflate response)))
