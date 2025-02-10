(ns gakki.accounts.ytm.search-suggest
  (:refer-clojure :exclude [load])
  (:require
   [applied-science.js-interop :as j]
   [gakki.accounts.ytm.api :refer [send-request]]
   [gakki.accounts.ytm.util :refer [runs->text single-key-child]]
   [promesa.core :as p]))

(defn- unpack-suggestion [^js container]
  (j/let [^:js {:keys [suggestion]} (single-key-child container)
          text (runs->text suggestion "")]
    (when text
      {:query text
       :formatted (->> (j/get suggestion :runs)
                       (map (j/fn [^:js {:keys [text bold]}]
                              (if bold
                                [:b text]
                                text))))})))

(defn load [client, query]
  (p/let [response (send-request client
                                 (j/lit
                                  {:endpoint "music/get_search_suggestions"
                                   :body {:input query}}))
          suggestions (j/get-in response [:contents
                                          0
                                          :searchSuggestionsSectionRenderer
                                          :contents])]
    (keep unpack-suggestion suggestions)))

#_:clj-kondo/ignore
(comment

  (-> (p/let [client (gakki.accounts.ytm.creds/account->client
                      @(re-frame.core/subscribe [:account :ytm]))
              result (load client "last")]
        (cljs.pprint/pprint result))
      (p/catch log/error)))
