(ns gakki.player.decode
  (:require ["prism-media" :as prism]))

(defn decode-stream
  "Given a config map and an encoded audio stream, return a stream that decodes
   the audio stream to 16bit signed PCM data. The config map must look like:

   {:channels <number>
    :sample-rate <hz number>

    :container \"webm\"  ; eg
    :codec \"opus\"}     ; eg
   "
  [config ^js stream]
  (let [demuxer (case (:container config)
                  "ogg" (prism/opus.OggDemuxer.)
                  "webm" (case (:codec config)
                           "opus" (prism/opus.WebmDemuxer.)
                           "vorbis" (prism/vorbis.WebmDemuxer.))
                  nil nil)

        ; TODO we ought to be able to fall back to prism.Ffmpeg
        decoder (case (:codec config)
                  "opus" (prism/opus.Decoder.
                           #js {:rate (:sample-rate config)
                                :channels (:channels config)
                                :frameSize 960}))

        demuxed (if demuxer
                  (.pipe stream demuxer)
                  stream)]
    (.pipe demuxed decoder)))
