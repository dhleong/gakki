(ns gakki.accounts.core)

(defprotocol IAccountProvider
  (get-name [this])
  (describe-account
    [this account]
    "Get the name/identifier of the account")

  (create-playable
    [this account playable-info]
    "Given the account and a playable info object (as returned from
     fetch-home), return an IPlayable instance for object.")

  (fetch-home
    [this account]
    "Return a promise resolving to a map of information about things
     to help render the home page:
     {:recent []
      :categories
      [{:name \"Category Name\"
        :items []}]}")

  (resolve-album
    [this account album-id]
    "Return a promise...")

  (resolve-artist
    [this account artist-id]
    "Return a promise...")

  (resolve-playlist
    [this account playlist-id]
    "Return a promise...")

  (resolve-radio
    [this account radio]
    "Return a promise...")

  (search
    [this account query])

  (search-suggest
    [this account partial-query]))
