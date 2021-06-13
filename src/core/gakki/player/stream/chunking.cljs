(ns gakki.player.stream.chunking
  (:require ["stream" :refer [Transform Readable]]))

(defn- concat-buffer [original to-concat]
  (js/Buffer.concat #js [original to-concat]))

(defn- create-nbytes-chunking-transform [n]
  (let [storage (atom (js/Buffer.alloc 0))]
    (Transform.
      #js {:transform
           (fn transform [chnk _encoding callback]
             (let [b (swap! storage concat-buffer chnk)]
               (loop [start 0
                      end n]
                 (if (<= end (.-length b))
                   (do (this-as this (.push this (.slice b start end)))
                       (recur (+ start n)
                              (+ end n)))

                   (do
                     (reset! storage (.slice b start))
                     (callback))))))})))

(defn ^Readable nbytes
  "Pipes the Readable input stream into and returns a Transform stream
   which chunks its input into Buffers of size divisible by `n`."
  [^Readable input, n]
  (.pipe input (create-nbytes-chunking-transform n)))
