(ns gakki.integrations
  (:require [gakki.integrations.discord :as discord]))

; NOTE: each entry in this map is `k -> f`, where `k` is a keyword by which we
; can enable the integration in prefs in the DB, and `f` is a function that
; accepts an integration-specific map of configuration (or nil, if disabled)
(def ^:private configure
  {:discord discord/configure!})

(defn configure! [configurations]
  (doseq [[k f] configure]
    (f (get configurations k))))

(def ^:private set-state
  [discord/set-state!])

(defn set-state! [state]
  (doseq [f set-state]
    (f state)))
