(ns gakki.fx
  (:require [archetype.util :refer [>evt]]
            [re-frame.core :refer [reg-fx]]
            [promesa.core :as p]
            [gakki.auth :as auth]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.remote :as remote]))

(reg-fx
  :auth/load!
  (fn []
    (p/let [accounts (auth/load-accounts)]
      (>evt [:auth/set accounts]))))

(reg-fx
  :providers/load!
  (fn [accounts]
    (>evt [:loading/update-count inc])
    (->> accounts
         (map (fn [[k account]]
                (when-let [provider (get providers k)]
                  (-> (p/let [results (ap/fetch-home provider account)]
                        (when results
                          (>evt [:home/replace k results])))
                      (p/catch (fn [e]
                                 ; TODO logging
                                 (println "[err: " k "] " e)))))))
         p/all
         (p/map (fn []
                  (>evt [:loading/update-count dec]))))))

(reg-fx
  :player/play!
  (fn [item]
    (remote/play! (:provider item) item)))

(reg-fx :player/unpause!  remote/unpause!)
(reg-fx :player/pause!  remote/pause!)
(reg-fx :player/set-volume!  remote/set-volume!)
