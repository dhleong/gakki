(ns gakki.accounts.ytm.home
  (:require [applied-science.js-interop :as j]
            [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
            [promesa.core :as p]))

(defn load-innertube [^js client]
  (p/let [home-feed (j/call-in client [.-music .-getHomeFeed])]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-feed home-feed)
    ; TODO extract continuation data
    {:categories (->> (j/get home-feed .-sections)
                      (keep music-shelf->section)
                      vec)}))
