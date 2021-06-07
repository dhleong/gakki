(ns gakki.accounts.ytm.search
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request
                                               generateBody generate-body}]
            [gakki.accounts.ytm.music-shelf :refer [music-shelf->section]]))

(def ^:private param-types
  {:uploads "agIYAw%3D%3D"})

(defn- perform-with [^YTMusic client {:keys [params query]}]
  (p/let [body (-> (generate-body #js {})
                   (j/assoc! :query query)
                   (j/assoc! :params (get param-types params params)))
          response (send-request (.-cookie client)
                                 (j/lit
                                   {:endpoint "search"
                                    :body body}))
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
      (p/catch #(do (cljs.pprint/pprint (ex-data %))
                    (println (.-stack %)))))
  
  )
