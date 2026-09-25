(ns gakki.accounts.ytm.playlist
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.music-shelf :refer [music-shelf->section
                                           parse-shelf-item]]
   [gakki.accounts.ytm.util :as util :refer [runs->text]]
   [promesa.core :as p]))

(defn- parse-items [^js response]
  (let [raw-shelves (j/get-in response [:contents
                                        :twoColumnBrowseResultsRenderer
                                        :secondaryContents
                                        :sectionListRenderer
                                        :contents])]
    ; TODO extract continuation data
    (->> raw-shelves
         (keep music-shelf->section)
         (mapcat :items)
         vec)))

(defn- inflate-header [^js response]
  (or (j/get-in
       response [:contents
                 :twoColumnBrowseResultsRenderer
                 :tabs
                 0
                 :tabRenderer
                 :content
                 :sectionListRenderer
                 :contents
                 0
                 :musicEditablePlaylistDetailHeaderRenderer
                 :header
                 :musicResponsiveHeaderRenderer])
      (j/get-in response [:header :musicDetailHeaderRenderer])
      (j/get-in response [:header
                          :musicEditablePlaylistDetailHeaderRenderer
                          :header
                          :musicDetailHeaderRenderer])))

(defn inflate [id kind, ^js response]
  (let [header (inflate-header response)]
    {:id id
     :provider :ytm
     :kind kind
     :title (runs->text (j/get header :title))
     :image-url (util/pick-thumbnail header)
     :items (parse-items response)}))

(defn inflate-innertube [id kind ^js playlist]
  #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
  (def ^:private last-playlist playlist)
  {:id id
   :provider :ytm
   :kind kind
   :title (str (j/get-in playlist [.-header .-title]))
   :image-url (some->
               (or (j/get-in playlist [.-header .-thumbnail])
                   (j/get-in playlist [.-header .-thumbnails])
                   (j/get playlist .-background))
               (util/pick-thumbnail))
   :items (vec (keep parse-shelf-item (j/get playlist .-contents)))})

(defn load-innertube [^js client, id]
  (p/let [playlist (j/call-in client [.-music .-getPlaylist] id)]
    (inflate-innertube id :playlist playlist)))
