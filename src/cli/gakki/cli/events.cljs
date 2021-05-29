(ns gakki.cli.events
  (:require [re-frame.core :refer [reg-event-db trim-v]]))

(reg-event-db
  ::set-dimens
  [trim-v]
  (fn [db [width height]]
    (assoc db
           :cli/width width
           :cli/height height)))
