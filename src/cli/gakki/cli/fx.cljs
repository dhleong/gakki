(ns gakki.cli.fx
  (:require [re-frame.core :refer [reg-fx]]
            [gakki.native :as native]))

(reg-fx
  :native/set-state!
  native/set-state!)

(reg-fx
  :native/set-now-playing!
  native/set-now-playing!)
