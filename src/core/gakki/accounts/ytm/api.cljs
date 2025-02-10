(ns gakki.accounts.ytm.api
  (:require
   ["ytmusic-api" :as YTMusic]
   ; ["ytmusic" :rename {YTMUSIC YTMusic}]
   ; ["ytmusic/dist/lib/utils" :rename {generateBody generate-body
   ;                                    sendRequest do-send-request}]
   ; [applied-science.js-interop :as j]
   [promesa.core :as p]
   [applied-science.js-interop :as j]))

(def YTMClient YTMusic)

; (defn create-client [cookies]
;   (YTMusic. cookies))

; (defn get-cookies [^YTMusic client]
;   (.-cookie client))

; (defn send-request [^YTMClient client request]
;   (p/let [request (if-some [body (j/get request :body)]
;                     (j/assoc! request :body (reduce
;                                              (fn [m k]
;                                                (j/assoc! m k (j/get body k)))
;                                              (generate-body #js {})
;                                              (js/Object.keys body)))
;                     request)
;           response (do-send-request (.-cookie client) request)]
;     #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :inline-def]}
;     (do
;       (def last-cookie (.-cookie client))
;       (def last-response response))
;     response))

(defn create-client [cookies]
  (atom {:client (YTMusic.)
         :cookies cookies
         :needs-initialized? true}))

(defn get-cookies [client]
  (:cookies @client))

(defn- ensure-initialized [client]
  (let [[{:keys [^YTMClient client cookies needs-initialized?]} _]
        (swap-vals! client dissoc :needs-initialized?)]
    (p/do!
     (when needs-initialized?
       (.initialize client cookies))
     client)))

(defn send-request [client request]
  #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :inline-def :redundant-do]}
  (do
    (def last-client client)
    (def last-request request))

  (p/let [^YTMClient ytm-client (ensure-initialized client)
          endpoint (j/get request :endpoint)
          req #js {:browseId (j/get request :id)}
          _ (def last-req req)
          response (.constructRequest
                    ytm-client
                    endpoint
                    req)]
    #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var :inline-def]}
    (do
      (def last-cookie (.-cookie client))
      (def last-response response))
    response))
