(ns gakki.accounts.ytm.search-suggest
  (:require [promesa.core :as p]))

; (defn- unpack-suggestion [^js container]
;   (j/let [^:js {:keys [suggestion]} (single-key-child container)
;           text (runs->text suggestion "")]
;     (when text
;       {:query text
;        :formatted (->> (j/get suggestion :runs)
;                        (map (j/fn [^:js {:keys [text bold]}]
;                               (if bold
;                                 [:b text]
;                                 text))))})))

(defn load [^YTMusic _client, _query]
  (throw (ex-info "not ready yet" {}))
  #_(p/let [body (-> (generate-body #js {})
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
      (p/catch log/error)))
