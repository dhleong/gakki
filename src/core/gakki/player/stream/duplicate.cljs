(ns gakki.player.stream.duplicate
  (:require ["stream" :refer [Transform Readable Writable]]))

(defn- create-duplicating-transform [^Writable destination]
  (Transform.
    #js {:transform
         (fn transform [chnk encoding callback]
           (this-as this
                    (.push this chnk))
           (.write destination chnk encoding callback))}))

(defn ^Readable to-stream
  "Pipes the Readable input stream into and returns a Transform stream
   which mirrors the input stream, but which also writes every byte it
   receives into the Writable `duplicate`."
  [^Readable input, ^Writable duplicate]
  (.pipe input (create-duplicating-transform duplicate)))
