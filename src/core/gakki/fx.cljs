(ns gakki.fx
  (:require [archetype.util :refer [>evt]]
            [re-frame.core :refer [reg-fx]]
            [promesa.core :as p]
            [gakki.auth :as auth]))

(reg-fx
  :auth/load!
  (fn []
    (p/let [accounts (auth/load-accounts)]
      (>evt [:auth/set accounts]))))
