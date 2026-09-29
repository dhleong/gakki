(ns gakki.accounts.ytm.creds
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["youtubei.js" :refer [Innertube Platform UniversalCache]]))

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

; TODO: Unify around a single fn
(def account->client get-authd-innertube)
