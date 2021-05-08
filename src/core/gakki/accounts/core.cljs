(ns gakki.accounts.core)

(defprotocol IAccountProvider
  (get-name [this])
  (describe-account [this account]))
