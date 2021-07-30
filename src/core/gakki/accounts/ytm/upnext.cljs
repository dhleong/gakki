(ns gakki.accounts.ytm.upnext
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request
                                               generateBody generate-body}]))

(defn load [^YTMusic client info]
  (p/let [body (-> (generate-body #js {})
                   (j/assoc! :playlistId (:playlist-id info)))
          response (send-request (.-cookie client)
                                 (j/lit
                                   {:endpoint "next"
                                    :body body}))]
    response))
