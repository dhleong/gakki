(ns gakki.accounts.core)

(defprotocol IAccountProvider
  (get-name [this])
  (describe-account
    [this account]
    "Get the name/identifier of the account")

  (fetch-home
    [this account]
    "Return a promise resolving to a map of information about things
     to help render the home page:
     {:recent []
      :categories
      [{:name \"Category Name\"
        :items []}]}"))
