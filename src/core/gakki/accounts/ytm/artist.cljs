(ns gakki.accounts.ytm.artist
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]))

(defn load [^YTMusic client id]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id id
                                      :type "ARTIST"
                                      :endpoint "browse"})
          
          ]
    (println "response=" response)
    ))
