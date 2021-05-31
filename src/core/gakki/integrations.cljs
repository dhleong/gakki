(ns gakki.integrations
  (:require [gakki.integrations.discord :as discord]))

(def ^:private set-state
  [discord/set-state!])

(defn set-state! [state]
  (doseq [f set-state]
    (f state)))
