(ns gakki.accounts.ytm.home
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

; TODO: Clean up DEPRECATED
#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn load [^YTMusic client]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id "FEmusic_home"
                                      :endpoint "browse"})]
    (inflate response)))

(defn load-innertube [^js client]
  (p/let [home-feed (j/call-in client [.-music .-getHomeFeed])]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-feed home-feed)
    ; TODO extract continuation data
    {:categories (->> (j/get home-feed .-sections)
                      (keep music-shelf->section)
                      vec)}))
