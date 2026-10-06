(ns gakki.accounts.ytm.upnext
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.music-shelf :refer [parse-shelf-item]]
   [promesa.core :as p]))

(defn- parse-items [response]
  {:items (->> (j/get response :contents)
               (map parse-shelf-item))
   :continuation (j/get response :continuation)})

(defn inflate [base, ^js response]
  (merge base
         {:provider :ytm
          :kind :radio}
         (parse-items response)))

(defn- parse-continuation [entity response]
  (let [panel (j/get response :continuation_contents)]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-panel panel)
    (inflate entity panel)))

(defn load-continuation [client entity continuation]
  (p/let [resp (j/call-in
                client [:session :actions :execute]
                "/next"
                (cond->
                 #js {:client "YTMUSIC"
                      :continuation continuation
                      :parse true}
                  (:playlist-id entity)
                  (j/assoc! :playlistId (:playlist-id entity))

                  (= :track (:radio/kind entity))
                  (j/assoc! :videoId (:id entity))

                  (:params entity)
                  (j/assoc! :params (:params entity))))]

    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-resp resp)
    (parse-continuation entity resp)))

(defn load [client {:keys [id] :as info}]
  (p/let [upnext (j/call-in client [:music :getUpNext] id)]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-upnext upnext)
    (inflate info upnext)))

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (-> (p/let [client
              (gakki.accounts.ytm.creds/account->client
               (:ytm @(re-frame.core/subscribe [:accounts])))
              result (load-continuation client {} (.-continuation last-upnext))]
        (println (js/JSON.stringify result nil 2)))
      (p/handle println)))
