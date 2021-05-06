(ns gakki.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)
