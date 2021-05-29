(ns gakki.fx
  (:require [archetype.util :refer [>evt]]
            [re-frame.core :refer [reg-fx]]
            [promesa.core :as p]
            [gakki.native :as native]
            [gakki.accounts.core :as ap]
            [gakki.accounts :refer [providers]]
            [gakki.player.remote :as remote]))

(reg-fx
  :auth/load!
  (fn []
    (p/let [accounts (native/load-accounts)]
      (>evt [:auth/set accounts]))))

(reg-fx
  :auth/save!
  (fn [[provider account]]
    (native/add-account provider account)))

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
  :providers/resolve-and-open
  (fn [[kind accounts entity]]
    ; NOTE: kind must be eg: :playlist, :album
    ; NOTE: entity must have :provider and :id keys
    (let [k (:provider entity)
          provider (get providers k)
          account (get accounts k)]
      (if (and provider account)
        (-> (p/let [f (case kind
                        :album ap/resolve-album
                        :artist ap/resolve-artist
                        :playlist ap/resolve-playlist)
                    result (f provider
                              account
                              (:id entity))]
              (if (or (seq (:items result))
                      (seq (:categories result)))
                (>evt [:player/on-resolved kind result :action/open])
                (println "[err: " k "] Empty " kind result)))

            (p/catch (fn [e]
                       ; TODO logging...
                       (println "[err: " k "] " e))))

        (println "[err: " k "] Invalid provider or no account")))))

(reg-fx
  :player/play!
  (fn [{:keys [item config]}]
    (remote/play! (:provider item) item config)))

(reg-fx
  :player/prepare!
  (fn [item]
    (remote/prepare! (:provider item) item)))

(reg-fx :player/unpause!  remote/unpause!)
(reg-fx :player/pause!  remote/pause!)
(reg-fx :player/set-volume!  remote/set-volume!)
