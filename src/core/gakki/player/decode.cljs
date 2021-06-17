(ns gakki.player.decode
  (:require [applied-science.js-interop :as j]
            [gakki.const :as const]
            ["prism-media" :as prism]
            [gakki.player.stream.chunking :as chunking]
            [gakki.util.logging :as log]))

(defn decode-stream
  "Given a config map and an encoded audio stream, return a stream that decodes
   the audio stream to 16bit signed PCM data. The config map must look like:

   {:channels <number>
    :sample-rate <hz number>

    :container \"webm\"  ; eg
    :codec \"opus\"}     ; eg
   "
  [{:keys [container codec] :as config} ^js stream]
  (let [demuxer (case container
                  "ogg" (prism/opus.OggDemuxer.)
                  "webm" (case codec
                           "opus" (prism/opus.WebmDemuxer.)
                           "vorbis" (prism/vorbis.WebmDemuxer.))

                  ; Assume no specific demuxer necessary:
                  nil)

        decoder (case codec
                  "opus" (prism/opus.Decoder.
                           #js {:rate (:sample-rate config)
                                :channels (:channels config)
                                :frameSize const/default-frame-size})

                  (do
                    ((log/of :player/decode)
                     "No optimized decoder for " codec
                     "; falling back to ffmpeg")
                    (prism/FFmpeg.
                      (j/lit
                        {:args [:-loglevel "0"
                                :-ac (:channels config)
                                :-i "-"
                                :-f "s16le"
                                :-acodec "pcm_s16le"
                                :-ac (:channels config)]}))))

        demuxed (if demuxer
                  (.pipe stream demuxer)
                  stream)
        decoded (.pipe demuxed decoder)]

    ; Ensure that the decoded data is chunked appropriately to match the
    ; configured :frame-size (important to make RtAudio/Audify happy)
    (chunking/nbytes-from-config decoded config)))
