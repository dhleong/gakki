(ns gakki.player.caching
  (:require [applied-science.js-interop :as j]
            ["env-paths" :as env-paths]
            ["fs" :rename {createWriteStream create-write-stream
                           createReadStream create-read-stream}]
            ["fs/promises" :as fs]
            ["path" :as path]
            [promesa.core :as p]))

(def ^:private cache-dir
  (-> (env-paths "gakki", #js {:suffix ""})
      (j/get :cache)))

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
          ^js output (create-write-stream tmp-path)]

    (p/create
      (fn [p-resolve p-reject]
        (doto stream
          (.pipe output)
          (.on "error" p-reject)
          (.once "readable" p-resolve)
          (.on "end" (fn []
                       (println "Completed download of " destination-path)
                       (complete-download tmp-path destination-path)))
          (.resume))
        (.on output "error" identity)))

    ; NOTE: this delay is a hack to avoid SIGILL; Perhaps if we can wait
    ; until the first bytes get transfered we can avoid this hack:
    (p/delay 100)

    (open-stream tmp-path)))

(defn caching [^String cache-key, promise-factory]
  (let [file-path (path/join cache-dir cache-key)]
    (-> (p/let [_ (fs/mkdir cache-dir #js {:recursive true})
                stream (open-stream file-path)]
          (println "opened cached")
          ; TODO extract config
          {:stream stream
           :config {:sample-rate 48000
                    :frame-size 960
                    :channels 2
                    :codec "opus"
                    :container "webm"}})

        (p/catch
          (fn [_]
            ; probably, we don't have it cached
            ; TODO we could potentially resume a partial download?
            (p/let [{:keys [stream config]} (promise-factory)
                    stream (caching-transform stream file-path)]
              {:stream stream
               :config config}))))))
