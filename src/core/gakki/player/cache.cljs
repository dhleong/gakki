(ns gakki.player.cache
  "Cache management for the Player"
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            ["fs/promises" :as fs]
            [goog.structs.LinkedMap]
            [goog.structs :refer [LinkedMap]]
            ["path" :as path]
            [promesa.core :as p]
            [gakki.util.logging :as log]
            [gakki.util.paths :as paths]))

(defprotocol ICache
  (size [this])
  (on-file-created [this path])
  (on-file-accessed [this path]))


; ======= Cache state management ==========================

(defn- create-state [max-size-bytes on-evicted]
  (atom {:files (LinkedMap.
                  0 ; max entries (0 = don't limit count)
                  true) ; evict in insertion order ("cache mode")
         :max-size max-size-bytes
         :on-evicted on-evicted
         :bytes 0}))

(defn- access [{:keys [files] :as state} path]
  (.get files path)
  state)

(defn- put-with-size
  [{:keys [files max-size on-evicted] :as state} path size]
  (let [contained? (.containsKey files path)]
    (.set files path {:path path
                      :size size})
    (loop [state (if contained?
                   state
                   (update state :bytes + size))]
      (if (<= (:bytes state) max-size)
        ; Still within max-size
        state

        (let [evicted (.pop files)]
          (recur (-> state
                     (update :bytes - (:size evicted))
                     (update :promises (fnil conj [])
                             (on-evicted (:path evicted))))))))))


; ======= FS implementation ===============================

(defn initialize-state-with-stats
  "Expects the state value (as with swap!) from (create-state) and a collection
   of `[path #js {:size, :atimeMs}]` pairs."
  [initial-state stats]
  (->> stats
       (sort-by #(j/get (second %) :atimeMs))
       (reduce
         (j/fn [s [path ^:js {:keys [size]}]]
           (put-with-size s path size))
         initial-state)))

(defn- initialize-fs-cache [root-path state]
  (log/with-timing-promise
    :cache/init
    (p/let [paths (fs/readdir root-path)
            stats (->> paths
                       (keep #(when-not (str/ends-with? % ".progress")
                                (p/let [path (path/join root-path %)
                                        stat (fs/stat path)]
                                  [path stat])))
                       p/all)]
      ((log/of :cache) "Initializing... " root-path)
      (let [{:keys [promises]} (swap! state initialize-state-with-stats stats)]
        (swap! state dissoc :promises)
        (p/all promises)))))

(deftype ^:private StatePoweredPromiseSizingCache [state]
  ICache
  (size [_this] (:max-size @state))

  (on-file-created [_this path]
    (-> (p/let [stat (fs/stat path)
                {:keys [promises]} (swap! state put-with-size
                                          path (j/get stat :size))]
          (swap! state dissoc :promises)
          (p/all promises))
        (p/catch (partial
                   log/error
                   "Unable to update cache for download of" path))))

  (on-file-accessed [_this path]
    (swap! state access path)))

(defn- ^ICache create-fs-with-state [state]
  (->StatePoweredPromiseSizingCache state))

(defn- on-evict-file [path]
  ((log/of :cache) "Evicting" path "...")
  (fs/rm path))

; ======= Public interface ================================

(defn create
  ([cache-size] (create (paths/platform :cache) cache-size))
  ([root-path cache-size]
   (let [state (create-state
                 cache-size
                 on-evict-file)]
     (-> (initialize-fs-cache root-path state)
         (p/catch (partial log/error "Error initializing fs-cache")))
     (create-fs-with-state state))))

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


#_:clj-kondo/ignore
(comment

  (-> (paths/platform :cache)
      (initialize-fs-cache (create-state
                             (* 1024 1024 1024)
                             on-evict-file))
      (p/then cljs.pprint/pprint)
      (p/catch log/error))

  )
