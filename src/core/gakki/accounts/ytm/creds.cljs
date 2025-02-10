(ns gakki.accounts.ytm.creds
  (:require
   ["youtubish/dist/creds" :refer [cached OauthCredentialsManager]]
   [applied-science.js-interop :as j]
   [archetype.util :refer [>evt]]
   [gakki.accounts.ytm.api :as api]
   [gakki.util.logging :as log]
   [promesa.core :as p]))

(defonce ^:private created-creds (atom nil))

(defonce account->creds
  (memoize
   (fn [account]
     (cached
      (OauthCredentialsManager.
       (clj->js account)
       #js {:persistCredentials
            (fn [creds]
              (let [updated (merge account
                                   (js->clj creds :keywordize-keys true))]
                (>evt [:auth/save :ytm updated {:load-home? false}])))})))))

(defn account->cookies [account]
  (p/let [initial? (nil? (get @created-creds account))
          start (js/Date.now)
          creds (account->creds account)
          cookies-obj (.get creds)
          delta (- (js/Date.now) start)]

    ; logging:
    (swap! created-creds assoc account true)
    (if initial?
      (log/timing :ytm/initial-cookie-fetch delta)
      (log/timing :ytm/cookie-refresh delta))

    (j/get cookies-obj :cookies)))

(defn account->client [account]
  (p/let [cookies (account->cookies account)]
    (api/create-client cookies)))
