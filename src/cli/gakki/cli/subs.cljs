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
      ; and one more for sanity
      (max 0 (- raw-height 5)))))

(reg-sub
  ::available-width
  :<- [::cli-dimens :width]
  (fn [raw-height [_ ?mod ?mod-amount]]
    (when raw-height
      ; subtract left-right borders
      (let [base (- raw-height 2)
            base (if ?mod
                   (?mod base ?mod-amount)
                   base)]
        (max 0 base)))))
