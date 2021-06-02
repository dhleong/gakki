(ns gakki.native.default
  (:require [applied-science.js-interop :as j]
            [cognitect.transit :as t]
            ["fs/promises" :as fs]
            ["keytar" :refer [deletePassword findCredentials setPassword]]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.util.logging :as log]
            [gakki.util.paths :as paths]))


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
  (-> (p/let [path (paths/user-config "prefs.json")
              raw (fs/readFile path)]
        (-> raw
            (js/JSON.parse)
            (js->clj :keywordize-keys true)))
      (p/catch (j/fn [^:js {:keys [code] :as e}]
                 (when-not (= "ENOENT" code)
                   (log/debug e))
                 nil))))



; ======= Persistent state ================================

(defn load-persistent-state []
  (-> (p/let [path (paths/internal-data "persistent-state.edn")
              raw (fs/readFile path)]
        (-> (t/reader :json)
            (t/read raw)))
      (p/catch (j/fn [^:js {:keys [code] :as e}]
                 (when-not (= "ENOENT" code)
                   (log/debug e))
                 nil))))

(defn save-persistent-state [state]
  (-> (p/let [path (paths/internal-data "persistent-state.edn")]
        (fs/mkdir (path/dirname path) #js {:recursive true})
        (fs/writeFile path
                      (-> (t/writer :json)
                          (t/write state))))
      (p/catch (fn [e]
                 (log/debug "Error persisting state" e)
                 nil))))


; ======= Commands declaration ============================

(def commands
  {:load-accounts load-accounts
   :add-account add-account
   :delete-account delete-account

   :load-prefs load-prefs

   :load-persistent-state load-persistent-state
   :save-persistent-state save-persistent-state
   })
