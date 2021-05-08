(ns gakki.cli
  (:require [reagent.core :as r]
            [re-frame.core :as re-frame]
            ["ink" :as k]
            [gakki.events :as events]
            [gakki.fx]
            [gakki.subs]
            [gakki.views :as views]))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (k/render (r/as-element [views/main])))

(defn ^:export init []
  (set! (.-title js/process) "gakki")

  ; stop re-frame loggers from trashing our cli UI
  (re-frame/set-loggers!
    (let [log (fn [& _]
                ; this is a nop, for now
                )]
      {:log      (partial log :info)
       :warn     (partial log :warn)
       :error    (partial log :error)
       :debug    (partial log :debug)
       :group    (partial log :info)
       :groupEnd  #()}))

  (re-frame/dispatch-sync [::events/initialize-db])
  (mount-root))
