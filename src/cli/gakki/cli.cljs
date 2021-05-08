(ns gakki.cli
  (:require [reagent.core :as r]
            [re-frame.core :as re-frame]
            ["ink" :as k]
            [gakki.events :as events]
            [gakki.fx]
            [gakki.subs]
            [gakki.util.logging :as logging]
            [gakki.views :as views]))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (k/render (r/as-element [views/main])))

(defn ^:export init []
  (set! (.-title js/process) "gakki")

  (logging/patch)

  (re-frame/dispatch-sync [::events/initialize-db])
  (mount-root))
