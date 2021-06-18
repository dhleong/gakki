(ns gakki.db
  (:require [gakki.const :as const]))

(def default-integrations
  {:discord {}})

(def default-db
  {:page [:home]
   :backstack []
   :accounts nil})

(def default-prefs
  {:cache.size const/default-cache-size})
