(ns gakki.accounts.ytm.api
  (:require
   ["ytmusic" :rename {YTMUSIC YTMusic}]
   ["ytmusic/dist/lib/utils" :rename {generateBody generate-body
                                      sendRequest do-send-request}]
   [applied-science.js-interop :as j]
   [promesa.core :as p]))

(def YTMClient YTMusic)

(defn create-client [cookies]
  (YTMusic. cookies))

(defn send-request [^YTMClient client request]
  (p/let [request (if-some [body (j/get request :body)]
                    (j/assoc! request :body (reduce
                                             (fn [m k]
                                               (j/assoc! m k (j/get body k)))
                                             (generate-body #js {})
                                             (js/Object.keys body)))
                    request)
          response (do-send-request (.-cookie client) request)]
    #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :inline-def]}
    (do
      (def last-cookie (.-cookie client))
      (def last-response response))
    response))
