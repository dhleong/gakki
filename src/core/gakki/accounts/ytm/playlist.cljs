(ns gakki.accounts.ytm.playlist
  (:refer-clojure :exclude [load])
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [gakki.accounts.ytm.api :refer [YTMClient send-request]]
            [promesa.core :as p]
            [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
            [gakki.accounts.ytm.util :as util :refer [runs->text]]))

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

(defn load [^YTMClient client, id]
  (p/let [response (send-request client
                                 #js {:id (if-not (str/starts-with? id "VL")
                                            (str "VL" id)
                                            id)
                                      :type "PLAYLIST"
                                      :endpoint "browse"})]
    #_{:clj-kondo/ignore [:inline-def :clojure-lsp/unused-public-var]}
    (def last-response response)
    (inflate id :playlist response)))

(comment
  (inflate :id :playlist last-response))
