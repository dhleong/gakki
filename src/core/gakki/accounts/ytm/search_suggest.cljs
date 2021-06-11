(ns gakki.accounts.ytm.search-suggest
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request
                                               generateBody generate-body}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.accounts.ytm.util :refer [runs->text
                                             single-key-child]]))

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

(defn load [^YTMusic client, query]
  (p/let [body (-> (generate-body #js {})
                   (j/assoc! :input query))
          response (send-request (.-cookie client)
                                 (j/lit
                                   {:endpoint "music/get_search_suggestions"
                                    :body body}))
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
      (p/catch #(do (cljs.pprint/pprint (ex-data %))
                    (println (.-stack %)))))
  
  )
