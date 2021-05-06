(ns gakki.accounts
  (:require [gakki.accounts.ytm :refer [->YTMAccountProvider]]))

(def providers {:ytm (->YTMAccountProvider)})
