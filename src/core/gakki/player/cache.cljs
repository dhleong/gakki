(ns gakki.player.cache
  "Cache management for the Player"
  (:require [gakki.util.paths :as paths]))

(defprotocol ICache
  (size [this])
  (on-file-created [this path])
  (on-file-accessed [this path]))

(deftype LruFsCache [cache-root max-size-bytes state]
  ICache
  (size [_this] max-size-bytes)

  (on-file-created [_this _path]
    (println "TODO"))

  (on-file-accessed [_this _path]
    (println "TODO")))

(defn create
  ([cache-size] (create (paths/platform :cache) cache-size))
  ([root-path cache-size]
   (->LruFsCache root-path cache-size (atom nil))))

(defn ensure-sized
  "Given a maybe-existing ICache object and a preferred size, return an ICache
   instance that has the preferred size. If `existing` is non-nil and has a size
   matching the preferred size, it is returned unchanged. Otherwise, a new ICache
   instance is created with the given cache-size and default cache path."
  [existing cache-size]
  (if (and existing (= cache-size
                       (size existing)))
    existing
    (create cache-size)))
