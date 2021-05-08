(ns gakki.accounts.ytm
  (:require [promesa.core :as p]
            [gakki.accounts.core :as ap :refer [IAccountProvider]]))

(deftype YTMAccountProvider []
  IAccountProvider
  (get-name [_this] "YouTube Music")
  (describe-account [_ account]
    (str (-> account :user :email)))

  (fetch-home [this account]
    (p/do!
      (println "TODO fetch home from" (ap/describe-account this account))
      (p/delay 750)
      nil)
    ))
