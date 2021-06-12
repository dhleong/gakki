(ns gakki.player.pcm
  (:require [gakki.util.logging :as log]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.util.paths :as paths]
            [gakki.player.pcm.caching :as caching]
            [gakki.player.pcm.core :as pcm]
            [gakki.player.pcm.disk :as disk]
            [gakki.player.pcm.promised :as promised]))

(def ^:private cache-dir
  (paths/platform :cache))

(defn create-disk-source [path]
  (disk/create path))

(defn create-caching-source [cache-key promise-factory]
  (let [destination-path (path/join cache-dir cache-key)]
    (promised/create
      (-> (let [source (create-disk-source destination-path)]
            (p/do!
              ; read-config to ensure it's not only *there* but *valid*
              (pcm/read-config source)
              (log/debug "opening cached @" destination-path)
              source))

          (p/catch
            (fn [e]
              (log/debug "ERROR opening cached @ " destination-path ":" e)

              ; probably, we don't have it cached
              ; TODO we could potentially resume a partial download?
              (p/let [{:keys [stream config]} (promise-factory)]
                (log/debug "downloading to " destination-path)
                (caching/create stream config destination-path))))))))
