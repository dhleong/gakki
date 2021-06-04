(ns gakki.player.pcm.caching
  (:require [clojure.string :as str]
            [gakki.util.logging :as log]
            [promesa.core :as p]
            ["stream" :refer [PassThrough Transform Writable]]
            ["fs" :rename {createWriteStream create-write-stream
                           createReadStream create-read-stream}]
            ["fs/promises" :as fs]
            [gakki.player.decode :refer [decode-stream]]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.pcm.disk :as disk]
            [gakki.player.stream.counting :as counting]))

(defn- pipe-temp-into [^Writable destination-stream
                       & {:keys [path offset get-complete? end?]
                          :or {end? false
                               offset 0}}]
  (let [from-tmp (create-read-stream path #js {:start offset})
        bytes-read (atom 0)
        handle-file-end (if end?
                          identity ; nothing to do; we're done downloading!

                          #(let [completed? (get-complete?)
                                 total-read @bytes-read]
                             (pipe-temp-into
                               destination-stream
                               :path (if completed?
                                       ; switch to the full file:
                                       (str/replace path #"\.progress$" "")
                                       path)
                               :offset (+ offset total-read)
                               :get-complete? get-complete?
                               :end? completed?)))]
    (doto (counting/nbytes-atom-inc from-tmp bytes-read)
      (.once "end" handle-file-end)
      (.pipe destination-stream #js {:end end?}))))

(deftype CachingPCMSource [config, ^String disk-path, ^String tmp-path, state]
  IPCMSource
  (seekable-duration [_this]
    (let [current-state @state]
      (if (:disk current-state)
        (pcm/seekable-duration
          (:disk current-state))

        (pcm/bytes-to-duration-with config (:decoded-bytes current-state)))))

  (read-config [_this] config)

  (duration-to-bytes [this duration-seconds]
    (p/let [config (pcm/read-config this)]
      (pcm/duration-to-bytes-with config duration-seconds)))

  (open-read-stream [_this]
    (let [current-state @state]
      (when-let [^Readable downloader (:in-progress-decode current-state)]

        ; Pipe the downloader stream into a blackhole destination stream
        ; to ensure it continues downloading
        (let [blackhole (Writable. #js {:write
                                        (fn write [_ _ cb] (cb))})]
          (.pipe downloader blackhole)

          (swap! state (fn [old-state]
                         (-> old-state
                             (assoc :blackhole blackhole)
                             (dissoc :in-progress-decode))))))

      (cond
        ; Easy case! If we have a :disk delegate, the download is complete
        (:disk current-state)
        (pcm/open-read-stream
          (:disk current-state))

        ; If we haven't yet provided the in-progress-decode stream, do that
        (:in-progress-decode current-state)
        (:in-progress-decode current-state)

        ; Here's where it gets tricky; the stream hasn't finished downloading,
        ; and we've already given away our clean, as-it-downloads stream. We
        ; now have to create a stream from the temp file that doesn't end early
        ; if it's read quickly through (that is, it will lazily contain any
        ; bytes downloaded later.
        :else
        (let [stream (PassThrough.)]
          (pipe-temp-into
            stream
            :path tmp-path
            :get-complete? #(some? (:disk @state)))
          (decode-stream config stream))))))

(defn- complete-download [tmp-path destination-path]
  (-> (fs/rename tmp-path destination-path)
      (p/catch
        (fn [err]
          (println "Error completing download:" err)))))

(defn create [^Readable stream, config destination-path]
  (let [tmp-path (str destination-path ".progress")
        ^Writable to-disk (create-write-stream tmp-path)
        state (atom nil)
        caching-transform (Transform.
                            #js {:transform
                                 (fn transform [chnk encoding callback]
                                   (this-as this
                                            (.push this chnk))
                                   (.write to-disk chnk encoding
                                           (fn [v]
                                             (swap! state update :written +
                                                    (.-length chnk))
                                             (callback v))))})]

    (doto stream
      (.pipe caching-transform)
      (.on "end" (fn []
                   (log/debug "Completed download of " destination-path)
                   (-> (complete-download tmp-path destination-path)
                       (p/then (fn []
                                 (swap! state assoc
                                        :disk (disk/create destination-path))))))))

    ; TODO can we cancel the download if the user skips past the song?
    ; This might look like a (dispose) method on the Source

    (swap! state assoc
           :in-progress-decode
           (-> (decode-stream config caching-transform)
               (counting/nbytes-atom-inc state :decoded-bytes)))

    (->CachingPCMSource
      config destination-path tmp-path state)))
