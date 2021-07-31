(ns gakki.accounts.ytm.upnext
  (:require [applied-science.js-interop :as j]
            [gakki.accounts.ytm.util :as util :refer [runs->text]]
            [gakki.util.logging :refer [with-timing-promise]]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request
                                               generateBody generate-body}]))

(defmulti parse-item (fn [container] (first (js/Object.keys container))))

(defmethod parse-item "playlistPanelVideoRenderer"
  [^js container]
  (j/let [^:js {renderer :playlistPanelVideoRenderer} container]
    {:id (j/get renderer :videoId)
     :kind :track
     :provider :ytm
     :image-url (util/pick-thumbnail renderer)
     :duration (some-> (j/get renderer :lengthText)
                       runs->text
                       util/->seconds)
     :album (-> (j/get renderer :longBylineText)
                util/split-runs-by-dots
                second)
     :artist (runs->text (j/get renderer :shortBylineText))
     :title (runs->text (j/get renderer :title))}))

(defn- parse-items [^js response]
  (let [raw-root (j/get-in response [:contents
                                     :singleColumnMusicWatchNextResultsRenderer
                                     :tabbedRenderer
                                     :watchNextTabbedResultsRenderer
                                     :tabs
                                     0
                                     :tabRenderer
                                     :content
                                     :musicQueueRenderer
                                     :content
                                     :playlistPanelRenderer])
        continuations (j/get raw-root :continuations)]
    {:items (->> (j/get raw-root :contents)
                 (map parse-item))
     :continuations continuations}))

(defn inflate [base, ^js response]
  (merge base
         {:provider :ytm
          :kind :radio}
         (parse-items response)))

(defn load [^YTMusic client info]
  (p/let [body (cond-> (generate-body #js {})
                 (:playlist-id info)
                 (j/assoc! :playlistId (:playlist-id info))

                 (= :track (:radio/kind info))
                 (j/assoc! :videoId (:id info))

                 (:params info)
                 (j/assoc! :params (:params info)))
          response (->> (send-request (.-cookie client)
                                      (j/lit
                                        {:endpoint "next"
                                         :body body}))
                        (with-timing-promise :ytm/upnext-load))]
    (inflate info response)))
