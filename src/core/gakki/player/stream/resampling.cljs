(ns gakki.player.stream.resampling
  (:require [applied-science.js-interop :as j]
            [gakki.player.stream.chunking :as chunking]
            ["prism-media" :default prism]
            ["stream" :refer [Readable]]))

(def ^:private FFmpeg (.-FFmpeg prism))

(defn convert-pcm-config [^Readable stream, old-config new-config]
  (let [transform (FFmpeg.
                   (j/lit
                    {:args [:-loglevel "0"
                            :-f "s16le"
                            :-ac (:channels old-config)
                            :-ar (:sample-rate old-config)
                            :-i "-"
                            :-ac (:channels new-config)
                            :-ar (:sample-rate new-config)
                            :-f "s16le"]}))]
    (-> stream
        (.pipe transform)
        (chunking/nbytes-from-config new-config))))
