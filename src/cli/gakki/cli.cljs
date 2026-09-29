(ns gakki.cli
  (:require ["ink" :as k]
            [gakki.cli.fx]
            [gakki.events :as events]
            [gakki.fx]
            [gakki.integrations.discord]
            [gakki.native :as native]
            [gakki.subs]
            [gakki.util.logging :as logging]
            [gakki.views :as views]
            [promesa.core :as p]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defonce ^:private ink-instance (atom nil))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)

  (let [app (r/as-element [views/main])]
    (if-let [^js instance @ink-instance]
      (.rerender instance app)

      (-> (reset! ink-instance (k/render app))
          (.waitUntilExit)
          (p/then (fn []
                    (js/process.exit)))))))

(defn ^:export init []
  (set! (.-title js/process) "gakki")

  (logging/patch)
  (re-frame/dispatch-sync [::events/initialize-db])
  (native/init)

  (mount-root))
