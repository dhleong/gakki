(ns gakki.cli
  (:require [reagent.core :as r]
            [re-frame.core :as re-frame]
            ["ink" :as k]
            [gakki.events :as events]
            [gakki.fx]
            [gakki.cli.fx]
            [gakki.integrations.discord]
            [gakki.subs]
            [gakki.native :as native]
            [gakki.util.logging :as logging]
            [gakki.views :as views]))

(defonce ^:private ink-instance (atom nil))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)

  (let [app (r/as-element [views/main])]
    (when-let [^js instance @ink-instance]
      ; NOTE: This is a brute-force way to ensure hot-reloads don't
      ; break reactive rendering (see #1). You would think we could use
      ;   (.rerender instance app)
      ; but that doesn't seem to be any different from just running
      ;   (k/render app)
      ; again. Clearing and unmounting the old instance works, however,
      ; and should not have any effect on the production app.
      (.clear instance)
      (.unmount instance))

    (reset! ink-instance (k/render app))))

(defn ^:export init []
  (set! (.-title js/process) "gakki")

  (logging/patch)
  (re-frame/dispatch-sync [::events/initialize-db])
  (native/init)

  (mount-root))
