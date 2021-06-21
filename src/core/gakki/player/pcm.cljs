(ns gakki.player.pcm
  (:require [archetype.util :refer [>evt]]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.util.loading :refer [with-loading-promise]]
            [gakki.util.logging :as log]
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
              (log/player "opening cached @" destination-path)

              ; The file seems fine; let's mark it as accessed for cache
              ; management purposes
              (>evt [:cache/file-accessed destination-path])
              source))

          (p/catch
            (fn [e]
              (log/player "ERROR opening cached @ " destination-path ":" e)

              ; probably, we don't have it cached
              ; TODO we could potentially resume a partial download?
              (p/let [{:keys [stream config]} (with-loading-promise
                                                :player.pcm/resolve-caching
                                                (promise-factory))]
                (log/player "downloading to " destination-path)
                (caching/create stream config destination-path))))))))
