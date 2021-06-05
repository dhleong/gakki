(ns gakki.player.stream.blackhole
  (:require ["stream" :refer [Writable]]))

(defn ^Writable create
  "Create a 'blackhole' Writable stream, which simply reads and throws away
   any bytes it is handed. This may be used to ensure a stream isn't stopped,
   for cases like `(duplicate/to-stream)` which might have side-effects.
   "
  []
  (Writable. #js {:write
                  (fn write [_ _ cb] (cb))}))
