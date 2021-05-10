(ns gakki.player.remote
  "Remote-control for the player subprocess"
  (:require [archetype.util :refer [>evt]]
            ["child_process" :refer [fork]]
            [cognitect.transit :as t]))

(def ^:private writer (t/writer :json))
(def ^:private reader (t/reader :json))

(defonce state (atom nil))

(defn- send! [state-obj message]
  (->> message
       (t/write writer)
       (.send (:process state-obj))))

(defn- ensure-launched! []
  (if-let [existing @state]
    existing

    (let [proc (fork "resources/player.js"
                     #js {:stdio "ignore"})
          clear-state! (fn [e]
                         (when e
                           (println "Player Exit:" e))
                         (reset! state nil))]
      (doto proc
        (.on "exit" clear-state!)
        (.on "error" clear-state!)
        (.on "message" (fn [raw-msg]
                         (let [msg (->> raw-msg
                                        (t/read reader))]
                           (>evt [:player/event msg])))))

      (doto (reset! state {:process proc})
        (send! {:type :init})))))

(defn pause! []
  (send! (ensure-launched!)
         {:type :pause}))

(defn play!
  ([provider-id info] (play! provider-id info nil))
  ([provider-id info config]
   (send! (ensure-launched!)
          {:type :play
           :provider provider-id
           :config config
           :info info})))

(defn set-volume! [volume-level]
  (send! (ensure-launched!)
         {:type :set-volume
          :level volume-level}))

(defn unpause! []
  (send! (ensure-launched!)
         {:type :unpause}))



(comment
  (swap! state (fn [{:keys [process]}]
                 (when process
                   (.kill process))
                 nil))

  (pause!)
  (unpause!)

  (set-volume! 0.5)

  (play! :ytm {:id "8FV4gcs-MNA"}))
