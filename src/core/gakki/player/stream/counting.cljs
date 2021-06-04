(ns gakki.player.stream.counting)

(defn- create-nbytes-counting-transform [on-count]
  (Transform.
    #js {:transform
         (fn count-transform [chnk _encoding callback]
           (this-as this (.push this chnk))
           (on-count (.-length chnk))
           (callback))}))

(defn nbytes-callback
  "Pipes the Readable input stream into and returns a Transform stream
   which calls `on-count` periodically with the number of bytes read"
  [^Readable input, on-count]
  (.pipe input (create-nbytes-counting-transform on-count)))

(defn nbytes-atom-inc
  "Pipes the Readable input stream into and returns a Transform stream which
   accumulates the number of read bytes into a map in the provided atom,
   optionally updating the given key instead of using the atom directly"
  ([^Readable input, storage-atom]
   (nbytes-callback input #(swap! storage-atom + %)))
  ([^Readable input, storage-atom atom-key]
   (nbytes-callback input #(swap! storage-atom update atom-key + %))))
