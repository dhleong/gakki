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
  :providers/resolve-and-open-playlist
  (fn [[accounts playlist]]
    (let [k (:provider playlist)
          provider (get providers k)
          account (get accounts k)]
      (if (and provider account)
        (-> (p/let [result (ap/resolve-playlist
                             provider
                             account
                             (:id playlist))]
              (if (seq (:items result))
                (>evt [:player/on-resolved :playlist result :action/open])
                (println "[err: " k "] Empty playlist: " result)))

            (p/catch (fn [e]
                       ; TODO logging...
                       (println "[err: " k "] " e))))

        (println "[err: " k "] Invalid provider or no account")))))

(reg-fx
  :player/play!
  (fn [{:keys [item config]}]
    (remote/play! (:provider item) item config)))

(reg-fx :player/unpause!  remote/unpause!)
(reg-fx :player/pause!  remote/pause!)
(reg-fx :player/set-volume!  remote/set-volume!)
