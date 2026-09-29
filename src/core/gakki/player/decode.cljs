(ns gakki.player.decode
  (:require [applied-science.js-interop :as j]
            ["prism-media" :default prism :refer [opus]]
            ["stream" :refer [Readable]]
            [gakki.const :as const]
            [gakki.player.stream.chunking :as chunking]
            [gakki.util.logging :as log]))

(def ^:private FFmpeg (.-FFmpeg prism))
(def ^:private VorbusWebmDemuxer (j/get-in prism [:vorbis :WebmDemuxer]))

(defn decode-stream
  "Given a config map and an encoded audio stream, return a stream that decodes
   the audio stream to 16bit signed PCM data. The config map must look like:

   {:channels <number>
    :sample-rate <hz number>

    :container \"webm\"  ; eg
    :codec \"opus\"}     ; eg
   "
  [{:keys [container codec] :as config} ^Readable stream]
  (let [demuxer (case container
                  "ogg" (opus.OggDemuxer.)
                  "webm" (case codec
                           "opus" (opus.WebmDemuxer.)
                           "vorbis" (VorbusWebmDemuxer.))

                  ; Assume no specific demuxer necessary:
                  nil)

        decoder (case codec
                  "opus" (opus.Decoder.
                          #js {:rate (:sample-rate config)
                               :channels (:channels config)
                               :frameSize const/default-frame-size})

                  (do
                    ((log/of :player/decode)
                     "No optimized decoder for " codec
                     "; falling back to ffmpeg")
                    (FFmpeg.
                     (j/lit
                      {:args [:-loglevel "0"
                              :-ac (:channels config)
                              :-i "-"
                              :-f "s16le"
                              :-acodec "pcm_s16le"
                              :-ac (:channels config)]}))))

        ^js demuxed (if demuxer
                      (.pipe stream demuxer)
                      stream)
        decoded (.pipe demuxed decoder)]

    ; Ensure that the decoded data is chunked appropriately to match the
    ; configured :frame-size (important to make RtAudio/Audify happy)
    (chunking/nbytes-from-config decoded config)))
