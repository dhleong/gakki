(ns gakki.accounts.ytm.creds
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            [promesa.core :as p]
            ["youtubish/dist/creds" :refer [cached OauthCredentialsManager]]
            ["youtubei.js" :refer [Innertube UniversalCache]]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.util.logging :as log]))

(defonce ^:private created-creds (atom nil))
(defonce ^:private innertube-ref (atom nil))
(defonce ^:private innertube-promise (Innertube.create
                                      #js {:cache (UniversalCache. false)}))

(defn- unpack-innertube-auth [^js credentials]
  {:access {:token (j/get credentials .-access_token)
            :type (j/get credentials .-token_type)
            :expires_at (-> credentials
                            (j/get .-expiry_date)
                            (js/Date.)
                            (.getTime))}
   :refresh {:token (j/get credentials .-refresh_token)}
   :scope (j/get credentials .-scope)})

(defn- pack-innertube-account [{:keys [access refresh scope]}]
  #js {:access_token (:token access)
       :expiry_date (js/Date. (:expires-at access))
       :token_type (:type access)
       :refresh_token (:token refresh)
       :scope scope})

(defn ^js get-authd-innertube [{:keys [cookie] :as account}]
  (if (or cookie (some? @innertube-ref))
    (p/let [old @innertube-ref]
      (if (= cookie (j/get-in old [.-session .-cookie]))
        old
        ; TODO: make caching persistent? manage cookies?
        (reset! innertube-ref
                (Innertube.create
                 #js {:cache (UniversalCache. false)
                      :cookie (:cookie account)}))))

    (p/let [^js yt innertube-promise]
      (when-not (j/get-in yt [.-session .-logged_in])
        (when-not account
          (throw (js/Error. "get-authd-innertube called without an account, and not already logged in")))
        (j/call-in yt [.-session .-signIn]
                   (pack-innertube-account account)))
      yt)))

(defn login-with-innertube [{:keys [on-url]}]
  (p/let [^js yt innertube-promise]
    (let [result (p/deferred)]
      (j/call-in yt [.-session .-removeAllListeners] "auth-pending")
      (j/call-in yt [.-session .-removeAllListeners] "auth")
      (j/call-in yt [.-session .-removeAllListeners] "update-credentials")
      (j/call-in yt [.-session .-on]
                 "auth-pending"
                 (fn [^js data]
                   (println data)
                   (on-url {:url (.-verification_url data)
                            :code (.-user_code data)})))
      (j/call-in yt [.-session .-on]
                 "auth"
                 (j/fn [^:js {:keys [credentials] :as resp}]
                   (println "resp=" resp)
                   (let [unpacked (unpack-innertube-auth credentials)]
                     (println "got " unpacked)
                     (p/resolve result unpacked))))
      (j/call-in yt [.-session .-on]
                 "update-credentials"
                 (j/fn [^:js {:keys [credentials]}]
                   (println "updated to: " credentials)
                   (j/call-in yt [.-auth .-cacheCredentials])))
      (j/call-in yt [.-session .-signIn])

      ; Return the deferred value:
      result)))

(defn get-innertube-user-info [account]
  (p/let [^js yt (get-authd-innertube account)
          container (j/call-in yt [.-account .-getInfo])
          accounts (j/get-in container [.-contents .-contents])
          selected (->> accounts
                        (filter (j/fn [^:js {:keys [is_selected]}]
                                  is_selected))
                        (first))]
    (when selected
      {:name (str (j/get selected .-account_name))
       :email (str (j/get selected .-account_byline))})))

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
  (if-some [s (:cookies account)]
    s

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

      (j/get cookies-obj :cookies))))

(defn account->client [account]
  (p/let [cookies (account->cookies account)]
    (YTMusic. cookies)))
