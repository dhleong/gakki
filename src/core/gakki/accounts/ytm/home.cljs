(ns gakki.accounts.ytm.home
  (:refer-clojure :exclude [load])
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.api :refer [YTMClient send-request]]
   [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
   [promesa.core :as p]))

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

(defn load [^YTMClient client]
  (p/let [response (send-request client
                                 #js {:id "FEmusic_home"
                                      :endpoint "browse"})]
    (inflate response)))
