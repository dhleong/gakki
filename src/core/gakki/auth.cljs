(ns gakki.auth
  (:require [applied-science.js-interop :as j]
            [cognitect.transit :as t]
            ["keytar" :refer [deletePassword findCredentials setPassword]]
            [promesa.core :as p]))

(def ^:private auth-service "io.github.dhleong.gakki")

(defn- deserialize-accounts [raw-list]
  (->> raw-list
       (reduce
         (j/fn [m ^:js {:keys [account password]}]
           (assoc m (keyword account) (-> (t/reader :json)
                                          (t/read password))))
         {})))

(defn load-accounts []
  (p/let [accounts-list (findCredentials auth-service)]
    (deserialize-accounts accounts-list)))

(defn add-account [kind account]
  (setPassword auth-service (name kind) (-> (t/writer :json)
                                            (t/write account))))

(defn delete-account [kind]
  (deletePassword auth-service (name kind)))
