(ns gakki.player.pcm
  (:require ["fs/promises" :as fs]
            [gakki.util.logging :as log]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.util.paths :as paths]
            [gakki.player.pcm.caching :as caching]
            [gakki.player.pcm.disk :as disk]
            [gakki.player.pcm.promised :as promised]))

(def ^:private cache-dir
  (paths/platform :cache))

(defn create-disk-source [path]
  (disk/create path))

(defn create-caching-source [cache-key promise-factory]
  (let [destination-path (path/join cache-dir cache-key)]
    (promised/create
      (-> (p/do!
            (fs/access destination-path)
            (log/debug "opening cached @" destination-path)
            (create-disk-source destination-path))

          (p/catch
            (fn [e]
              (log/debug "ERROR opening cached @ " destination-path ":" e)

              ; probably, we don't have it cached
              ; TODO we could potentially resume a partial download?
              (p/let [{:keys [stream config]} (promise-factory)]
                (log/debug "downloading to " destination-path)
                (caching/create stream config destination-path))))))))
