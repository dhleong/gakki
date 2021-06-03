(ns gakki.player.seek
  (:require ["stream" :refer [Readable Transform]]))

(defn- create-skip-bytes-transform [n]
  (let [to-skip (atom n)]
    (Transform.
      #js {:transform
           (fn seek-transform [chnk _encoding callback]
             (let [skip-remaining @to-skip
                   chunk-size (.-length chnk)]
               (cond
                 (<= skip-remaining 0)
                 (this-as this (.push this chnk))

                 (>= skip-remaining chunk-size)
                 (swap! to-skip - chunk-size)

                 :else
                 (do
                   (reset! to-skip 0)
                   (this-as this (.push this (.slice chnk skip-remaining)))))

               (callback)))})))

(defn nbytes [^Readable input, n]
  (if (= 0 n)
    input
    (let [tf (create-skip-bytes-transform n)]
      (.pipe input tf))))
