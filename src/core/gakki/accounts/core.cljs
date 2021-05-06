(ns gakki.accounts.core)

(defprotocol IAccountProvider
  (get-name [this]))
