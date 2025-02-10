(ns gakki.accounts.ytm.artist
  (:refer-clojure :exclude [load])
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.api :refer [YTMClient send-request]]
   [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
   [gakki.accounts.ytm.util :refer [runs->text unpack-navigation-endpoint]]
   [gakki.const :as const]
   [promesa.core :as p]))

(defn- unpack-playlist [header button-key title]
  (when-let [radio (-> header
                       (j/get-in [button-key
                                  :buttonRenderer])
                       unpack-navigation-endpoint)]
    (assoc radio
           :radio/kind (:kind radio)
           :kind :radio
           :title title)))

(defn load [^YTMClient client id]
  (p/let [response (send-request client
                                 #js {:id id
                                      :type "ARTIST"
                                      :endpoint "browse"})
          raw-rows (-> response
                       (j/get-in [:contents
                                  :singleColumnBrowseResultsRenderer
                                  :tabs
                                  0
                                  :tabRenderer
                                  :content
                                  :sectionListRenderer
                                  :contents]))
          header (j/get-in response [:header :musicImmersiveHeaderRenderer])
          title (-> header
                    (j/get :title)
                    (runs->text))]

    (when const/debug?
      #_:clj-kondo/ignore
      (def last-response response))

    {:id id
     :kind :artist
     :provider :ytm
     :title title
     :description (-> header
                      (j/get :description)
                      (runs->text))
     :radio (unpack-playlist header :startRadioButton (str title " Radio"))
     :shuffle (unpack-playlist header :playButton (str "Shuffle " title))
     :categories (keep music-shelf->section raw-rows)}))
