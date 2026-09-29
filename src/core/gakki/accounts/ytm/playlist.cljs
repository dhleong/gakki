(ns gakki.accounts.ytm.playlist
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.music-shelf :refer [parse-shelf-item]]
   [gakki.accounts.ytm.util :as util]
   [promesa.core :as p]))

(defn inflate [id kind ^js playlist]
  #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
  (def ^:private last-playlist playlist)
  {:id id
   :provider :ytm
   :kind kind
   :title (str (j/get-in playlist [:header :title]))
   :image-url (some->
               (or (j/get-in playlist [:header :thumbnail])
                   (j/get-in playlist [:header :thumbnails])
                   (j/get playlist :background))
               (util/pick-thumbnail))
   :items (vec (keep parse-shelf-item (j/get playlist :contents)))})

(defn load-innertube [^js client, id]
  (p/let [playlist (j/call-in client [.-music .-getPlaylist] id)]
    (inflate id :playlist playlist)))
