(ns gakki.cli.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::cli-dimens
  (fn [db [_ ?specific-dimen]]
    (case ?specific-dimen
      :width (:cli/width db)
      :height (:cli/height db)
      {:width (:cli/width db)
       :height (:cli/height db)})))

(reg-sub
  ::available-height
  :<- [::cli-dimens :height]
  (fn [raw-height _]
    (when raw-height
      ; subtract top/bottom borders + header and margin
      (- raw-height 4))))
