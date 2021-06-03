(ns gakki.player.caching
  (:require ["fs" :rename {createWriteStream create-write-stream
                           createReadStream create-read-stream}]
            ["fs/promises" :as fs]
            ["path" :as path]
            ["stream" :refer [Writable Transform]]
            [promesa.core :as p]
            [gakki.player.analyze :refer [analyze-audio]]
            [gakki.util.logging :as log]
            [gakki.util.paths :as paths]))

(def ^:private cache-dir
  (paths/platform :cache))

(defn- open-stream [path]
  (p/create
    (fn [p-resolve p-reject]
      (let [s (create-read-stream path)]
        (doto s
          (.on "error" p-reject)
          (.on "open" #(p-resolve s)))))))

(defn- complete-download [tmp-path destination-path]
  (-> (fs/rename tmp-path destination-path)
      (p/catch
        (fn [err]
          (println "Error completing download:" err)) )))

(defn- caching-transform [^js stream destination-path]
  (p/let [tmp-path (str destination-path ".progress")
          ^Writable to-disk (create-write-stream tmp-path)
          transform (Transform.
                      #js {:transform
                           (fn transform [chnk encoding callback]
                             (this-as this
                                (.push this chnk))
                             (.write to-disk chnk encoding callback))})]

    ; TODO can we cancel the download if the user skips past this song?

    (p/create
      (fn [p-resolve p-reject]
        (doto stream
          (.pipe transform)
          (.on "error" p-reject)
          (.once "readable" p-resolve)
          (.on "end" (fn []
                       (log/debug "Completed download of " destination-path)
                       (complete-download tmp-path destination-path)))
          (.resume))
        (.on to-disk "error" identity)))

    transform))

(defn caching [^String cache-key, promise-factory]
  (let [file-path (path/join cache-dir cache-key)]
    (-> (p/let [_ (fs/mkdir cache-dir #js {:recursive true})
                stream (open-stream file-path)
                config (analyze-audio file-path)]
          (log/debug "opened cached")
          {:stream stream
           :config config})

        (p/catch
          (fn [_]
            ; probably, we don't have it cached
            ; TODO we could potentially resume a partial download?
            (p/let [{:keys [stream config]} (promise-factory)
                    stream (caching-transform stream file-path)]
              {:stream stream
               :config config}))))))
