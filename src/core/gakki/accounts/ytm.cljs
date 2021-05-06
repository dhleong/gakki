(ns gakki.accounts.ytm
  (:require [gakki.accounts.core :refer [IAccountProvider]]))

(deftype YTMAccountProvider []
  IAccountProvider
  (get-name [_this] "YouTube Music"))