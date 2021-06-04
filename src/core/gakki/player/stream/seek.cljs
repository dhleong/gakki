(ns gakki.player.stream.seek
  (:require ["stream" :refer [Readable Transform]]))

(defn- create-nbytes-chunkwise-transform [n]
  (let [to-skip (atom n)]
    (Transform.
      #js {:transform
           (fn seek-transform [chnk _encoding callback]
             (let [skip-remaining @to-skip
                   chunk-size (.-length chnk)]
               (if (< skip-remaining chunk-size)
                 ; NOTE: We may actually want to skip part of this chunk,
                 ; but since this is chunkwise, we just send the whole chunk.
                 ; This is mostly to ensure that we always send complete PCM
                 ; blocks to not break output. If we ever needed a more precise
                 ; seek then it's simply a matter of slicing off the remainder
                 ; from the beginning of the chunk.
                 (this-as this (.push this chnk))

                 (swap! to-skip - chunk-size))

               (callback)))})))

(defn nbytes-chunkwise [^Readable input, n]
  (if (= 0 n)
    input
    (let [tf (create-nbytes-chunkwise-transform n)]
      (.pipe input tf))))
