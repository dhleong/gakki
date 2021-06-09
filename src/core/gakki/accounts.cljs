(ns gakki.accounts
  (:require [promesa.core :as p]
            [gakki.accounts.ytm :refer [->YTMAccountProvider]]
            [gakki.accounts.core :as ap]))

(def providers {:ytm (->YTMAccountProvider)})

(defn search [accounts query]
  (p/let [all-result-lists (->> accounts
                                (keep (fn [[k account]]
                                        (when-let [provider (get providers k)]
                                          (p/let [result (ap/search provider account query)]
                                            (:categories result)))))
                                p/all)]
    (vec (apply interleave all-result-lists))))

(defn search-suggest [accounts input]
  (p/let [all-result-lists (->> accounts
                                (keep (fn [[k account]]
                                        (when-let [provider (get providers k)]
                                          (ap/search-suggest provider account input))))
                                p/all)]
    (vec (apply interleave all-result-lists))))
