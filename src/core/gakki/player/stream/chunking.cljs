(ns gakki.player.stream.chunking
  (:require ["stream" :refer [Transform Readable]]))

(defn create-nbytes-chunking-transform-old [n]
  (Transform.
    #js {:transform
         (fn transform [chnk _encoding callback]
           (cond
             (= (.-length chnk) n)
             (do (this-as this (.push this chnk))
                 (callback))

             ; An even number of chnks combined
             (= 0 (mod (.-length chnk) n))
             (loop [i 0]
               (if (< i (.-length chnk))
                 (do (this-as this (.push this (.slice chnk i (+ i n))))
                     (recur (+ i n)))
                 (callback)))

             :else
             (throw (ex-info
                      (str "Unexpected chunk size: " (.-length chnk))
                      {:chunk-length (.-length chnk)
                       :n n}))))}))

(defn- create-nbytes-chunking-transform [n]
  (let [storage (atom (js/Buffer.alloc 0))]
    (Transform.
      #js {:transform
           (fn transform [chnk _encoding callback]
             (let [b (swap! storage #(js/Buffer.concat #js [% chnk]))]
               (loop [start 0
                      end n]
                 (if (< end (.-length chnk))
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
