(ns gakki.accounts.ytm.creds
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["youtubei.js" :refer [Innertube Platform UniversalCache]]
            [gakki.util.logging :as log]))

(defonce ^:private created-creds (atom nil))
(defonce ^:private innertube-ref (atom nil))

(j/assoc-in! Platform [.-shim .-eval]
             (j/fn [^:js {:keys [output]}]
               (p/do
                 ((js/Function. output)))))

(defn ^js get-authd-innertube [{:keys [cookie] :as account}]
  (p/let [old @innertube-ref]
    (if (= cookie (j/get-in old [.-session .-cookie]))
      old
      ; TODO: make caching persistent? manage cookies?
      (reset! innertube-ref
              (Innertube.create
               #js {:cache (UniversalCache. false)
                    :cookie (:cookie account)})))))

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
  (fn [_]
    (throw (ex-info "not supported yet" {}))))

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
  (p/let [_cookies (account->cookies account)]
    ; TODO: This could be a youtubei.js client?
    (throw (ex-info "not yet supported" {}))))
