(ns gakki.accounts.ytm.upnext
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.api :refer [send-request]]
   [gakki.accounts.ytm.util :as util :refer [runs->text]]
   [gakki.util.logging :as log :refer [with-timing-promise]]
   [promesa.core :as p]))

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

(defmethod parse-item "playlistPanelVideoWrapperRenderer"
  [^js container]
  (j/let [^:js {{wrapped :primaryRenderer} :playlistPanelVideoWrapperRenderer} container]
    (parse-item wrapped)))

(defmethod parse-item :default
  [^js container]
  ; Debug helper:
  #_:clj-kondo/ignore
  (def failed-container container)

  (throw (ex-info (str "No multimethod for ytm.upnext/parse-item: "
                       (first (js/Object.keys container)))
                  {:container (js->clj container)})))

(defn- parse-items [^js response]
  (let [raw-root (or (j/get-in response [:contents
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
                     (j/get-in response [:continuationContents
                                         :playlistPanelContinuation]))
        continuations (j/get raw-root :continuations)]
    {:items (->> (j/get raw-root :contents)
                 (map parse-item))
     :continuations continuations}))

(defn inflate [base, ^js response]
  (merge base
         {:provider :ytm
          :kind :radio}
         (parse-items response)))

(defn load [client info]
  (p/let [body (cond-> #js {}
                 (:playlist-id info)
                 (j/assoc! :playlistId (:playlist-id info))

                 (= :track (:radio/kind info))
                 (j/assoc! :videoId (:id info))

                 (:params info)
                 (j/assoc! :params (:params info))

                 (:continuation info)
                 (j/assoc! :continuation (:continuation info))

                 (:index info)
                 (j/assoc! :index (:index info))

                 (:click-tracking-params info)
                 (j/assoc-in! [:clickTracking :clickTrackingParams]
                              (:click-tracking-params info)))
          response (->> (send-request client
                                      (j/lit
                                       {:endpoint "next"
                                        :body body}))
                        (with-timing-promise :ytm/upnext-load))]
    (-> info
        (inflate response)
        (dissoc :continuation :index :click-tracking-params))))
