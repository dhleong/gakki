(ns gakki.accounts
  (:require [promesa.core :as p]
            [gakki.accounts.ytm :refer [->YTMAccountProvider]]
            [gakki.accounts.core :as ap]))

(def providers {:ytm (->YTMAccountProvider)})

(defn search-suggest [accounts input]
  (p/let [all-result-lists (->> accounts
                                (keep (fn [[k account]]
                                        (when-let [provider (get providers k)]
                                          (ap/search-suggest provider account input))))
                                p/all)]
    (apply interleave all-result-lists)))
