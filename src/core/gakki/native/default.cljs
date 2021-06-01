(ns gakki.native.default
  (:require [applied-science.js-interop :as j]
            [cognitect.transit :as t]
            ["fs/promises" :as fs]
            [gakki.util.logging :as log]
            ["keytar" :refer [deletePassword findCredentials setPassword]]
            ["os" :as os]
            ["path" :as path]
            [promesa.core :as p]))

(defn- config-dir
  ([] (path/join
        (os/homedir)
        ".config"
        "gakki"))
  ([sub-dir]
   (path/join (config-dir) sub-dir)))

; ======= auth commands ===================================

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


; ======= prefs commands ==================================

(defn load-prefs []
  (-> (p/let [path (config-dir "prefs.json")
              raw (fs/readFile path)]
        (-> raw
            (js/JSON.parse)
            (js->clj :keywordize-keys true)))
      (p/catch (j/fn [^:js {:keys [code] :as e}]
                 (when-not (= "ENOENT" code)
                   (log/debug e))
                 nil))))


(def commands
  {:load-accounts load-accounts
   :add-account add-account
   :delete-account delete-account

   :load-prefs load-prefs
   })
