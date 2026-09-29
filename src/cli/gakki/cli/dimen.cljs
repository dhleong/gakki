(ns gakki.cli.dimen
  (:require ["ink" :as k]
            [applied-science.js-interop :as j]
            [gakki.cli.events :as events]
            [re-frame.core :as rf]))

(defn dimens-tracker []
  (j/let [^:js {:keys [columns rows]} (k/useWindowSize)]
    (rf/dispatch-sync [::events/set-dimens columns rows])))
