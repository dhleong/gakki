(ns gakki.const
  (:require [gakki.util.bytesize :as bytesize]))

(def debug? goog/DEBUG)

(def max-volume-int 10)
(def suppressed-volume-percent 0.20)

(def default-cache-size (bytesize/parse "1G"))

; TODO Not sure how this should be determined; maybe it's just a buffer size and
; doesn't really matter what value it is...
(def default-frame-size 960)

; NOTE: We currently *always* decode to 16bit signed PCM data, so 2 bytes per sample
(def bytes-per-sample 2)

(goog-define discord-app-id "")
(goog-define discord-oauth-secret "")
