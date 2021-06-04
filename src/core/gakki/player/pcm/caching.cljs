(ns gakki.player.pcm.caching
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [gakki.util.logging :as log]
            [promesa.core :as p]
            ["stream" :refer [PassThrough Writable]]
            ["fs" :rename {createWriteStream create-write-stream
                           createReadStream create-read-stream}]
            ["fs/promises" :as fs]
            [gakki.player.decode :refer [decode-stream]]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.pcm.disk :as disk]
            [gakki.player.stream.blackhole :as blackhole]
            [gakki.player.stream.counting :as counting]
            [gakki.player.stream.duplicate :as duplicate]))

(declare ^:private pipe-temp-into)

(defn- restart-pipe [id source-state-atom, ^Writable destination-stream
                     & {:keys [path new-offset]}]
  (let [{:keys [bytes-written disk]} @source-state-atom
        completed? (some? disk)]
    (if (or completed? (> bytes-written new-offset))
      (do
        (log/debug "Partial pipe #" id " has more data (completed? " completed?
                   "; " bytes-written " > " new-offset "); resuming!")

        ; remove the old watch; we're about to create a new one
        (remove-watch source-state-atom [id :still-current-partial?])

        (pipe-temp-into
          id source-state-atom
          destination-stream
          :path (if completed?
                  ; switch to the full file:
                  (str/replace path #"\.progress$" "")
                  path)
          :offset new-offset
          :end? completed?))

      ; wait patiently for more bytes to be written to the file
      (do
        (log/debug "Partial pipe #" id " waiting for more data...")
        (add-watch source-state-atom [id :more-bytes-available?]
                   (fn [_k _r _old {:keys [bytes-written] completed? :disk}]
                     (when (or completed? (> bytes-written new-offset))
                       (remove-watch source-state-atom [id :more-bytes-available?])
                       (restart-pipe id source-state-atom destination-stream
                                     :path path
                                     :new-offset new-offset))))))))

(defn- pipe-temp-into [id source-state-atom,
                       ^Writable destination-stream
                       & {:keys [path offset end?]
                          :or {end? false
                               offset 0}}]
  (let [from-tmp (create-read-stream path #js {:start offset})
        bytes-read (atom 0)
        handle-file-end (if end?
                          identity ; nothing to do; we're done downloading!

                          #(let [new-offset (+ offset @bytes-read)]
                             (log/debug "Partial pipe #" id
                                        " reached end of stream @" new-offset
                                        " ...")
                             (restart-pipe
                               id source-state-atom destination-stream
                               :path path
                               :new-offset new-offset)))
        input-stream (counting/nbytes-atom-inc from-tmp bytes-read)]

    ; If a new stream is opened, we should clean up
    (add-watch source-state-atom [id :still-current-partial?]
               (fn [_k _r _old new-state]
                 (when-not (= id (:current-partial-stream new-state))
                   (log/debug "Partial pipe #" id " is no longer current; cleaning up")
                   (remove-watch source-state-atom [id :more-bytes-available?])
                   (remove-watch source-state-atom [id :still-current-partial?])
                   (.off input-stream "end" handle-file-end)
                   (.unpipe input-stream destination-stream))))

    (log/debug "Partial pipe #" id " writing @ " offset "; end? " end?)
    (doto input-stream
      (.once "end" handle-file-end)
      (.once "error" (j/fn [^:js {:keys [code] :as e}]
                       (if (and (= "ENOENT" code)
                                (not end?))
                         (do (log/debug "Partial pipe #" id
                                        "hit ENOENT; the download may have completed?")
                             (restart-pipe
                               id source-state-atom destination-stream
                               :path path
                               :new-offset offset))

                         (throw e))))
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
        (let [blackhole (blackhole/create)]
          (.pipe downloader blackhole)

          (swap! state (fn [old-state]
                         (-> old-state
                             (assoc :blackhole blackhole)
                             (dissoc :in-progress-decode))))))

      (cond
        ; Easy case! If we have a :disk delegate, the download is complete
        (:disk current-state)
        (do
          (swap! state dissoc :current-partial-stream)
          (pcm/open-read-stream
            (:disk current-state)))

        ; If we haven't yet provided the in-progress-decode stream, do that
        (:in-progress-decode current-state)
        (:in-progress-decode current-state)

        ; Here's where it gets tricky; the stream hasn't finished downloading,
        ; and we've already given away our clean, as-it-downloads stream. We
        ; now have to create a stream from the temp file that doesn't end early
        ; if it's read quickly through (that is, it will lazily contain any
        ; bytes downloaded later.
        :else
        (let [{id :current-partial-stream} (swap! state update
                                                  :current-partial-stream inc)
              stream (PassThrough.)]
          (pipe-temp-into
            id
            state
            stream
            :path tmp-path)
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
        caching-stream (-> stream
                           (duplicate/to-stream to-disk)
                           (counting/nbytes-atom-inc state :bytes-written))]

    (doto stream
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
           (-> (decode-stream config caching-stream)
               (counting/nbytes-atom-inc state :decoded-bytes)))

    (->CachingPCMSource
      config destination-path tmp-path state)))
