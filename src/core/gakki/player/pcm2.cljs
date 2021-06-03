(ns gakki.player.pcm2
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
  (promised/create
    (-> (p/let [path (path/join cache-dir cache-key)
                _ (fs/access path)]
          (log/debug "opened cached")
          (create-disk-source path))

        (p/catch
          (fn [_]
            ; probably, we don't have it cached
            ; TODO we could potentially resume a partial download?
            (p/let [{:keys [stream config]} (promise-factory)]
              (caching/create stream config path)))))))
