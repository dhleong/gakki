(ns gakki.accounts.ytm.search
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.api :refer [send-request]]
   [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]
   [promesa.core :as p]))

(def ^:private param-types
  {:uploads "agIYAw%3D%3D"})

(defn- perform-with [client {:keys [params query]}]
  (p/let [response (send-request client
                                 (j/lit
                                  {:endpoint "search"
                                   :body {:query query
                                          :params (get param-types params params)}}))
          tabs (j/get-in response [:contents
                                   :tabbedSearchResultsRenderer
                                   :tabs])
          selected (->> tabs
                        (some
                         #(when (j/get-in % [:tabRenderer :selected])
                            (j/get % :tabRenderer))))
          shelves (j/get-in selected [:content
                                      :sectionListRenderer
                                      :contents])]
    (keep music-shelf->section shelves)))

(defn perform [^YTMusic client query]
  (p/let [[categories uploads] (p/all [(perform-with client {:query query})
                                       (perform-with client {:query query
                                                             :params :uploads})])
          uploads (when (seq (:items (first uploads)))
                    (assoc (first uploads) :title "Uploads"))]
    {:categories (if uploads
                   (cons uploads categories)
                   categories)}))

#_:clj-kondo/ignore
(comment

  (-> (p/let [client (gakki.accounts.ytm.creds/account->client
                      @(re-frame.core/subscribe [:account :ytm]))
              result (perform client "last of us")]
        (cljs.pprint/pprint result))
      (p/catch log/error)))
