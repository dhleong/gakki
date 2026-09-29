(ns gakki.accounts.ytm.album
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            [gakki.accounts.ytm.playlist :as playlist]))

(defn inflate [album id]
  (playlist/inflate id :album album))

(defn load [yt id]
  (p/let [album (j/call-in yt [:music :getAlbum] id)]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-album album)
    (inflate album id)))

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (p/let [^js yt (gakki.accounts.ytm.creds/account->client
                  (:ytm @(re-frame.core/subscribe [:accounts])))
          result (load yt "MPREb_tW7vB7NqVhd")]
    (println result)))
