(ns gakki.accounts.ytm.artist
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
   [gakki.accounts.ytm.util :refer [unpack-navigation-endpoint]]
   [promesa.core :as p]))

(defn- unpack-playlist [header title]
  (when-let [radio (unpack-navigation-endpoint header)]
    (assoc radio
           :radio/kind (:kind radio)
           :kind :radio
           :title title)))

(defn load [^js client id]
  (p/let [artist (j/call-in client [:music :getArtist] id)
          title (str (j/get-in artist [:header :title]))]
    #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
    (def ^:private last-artist artist)
    {:id id
     :kind :artist
     :provider :ytm
     :title title
     :description (str (j/get-in artist [:header :description]))
     :radio (unpack-playlist
             (j/get-in artist [:header :start_radio_button])
             (str title " Radio"))
     :shuffle (unpack-playlist
               (j/get-in artist [:header :play_button])
               (str "Shuffle " title))
     :categories (keep music-shelf->section
                       (j/get artist :sections))}))

#_{:clj-kondo/ignore [:unresolved-namespace]}
(comment
  (p/let [^js yt (gakki.accounts.ytm.creds/account->client
                  (:ytm @(re-frame.core/subscribe [:accounts])))
          artist (load yt "UC37hiyVk7XSOY8EmnN_VmPQ")]
    (println artist)))
