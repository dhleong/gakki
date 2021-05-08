(ns gakki.player.remote
  "Remote-control for the player subprocess"
  (:require ["child_process" :refer [fork]]
            [cognitect.transit :as t]))

(def ^:private writer (t/writer :json))

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
          clear-state! #(reset! state nil)]
      (doto proc
        (.on "exit" clear-state!)
        (.on "error" clear-state!))

      (doto (reset! state {:process proc})
        (send! {:type :init})))))

(defn pause! []
  (send! (ensure-launched!)
         {:type :pause}))

(defn play! [provider-id info]
  (send! (ensure-launched!)
         {:type :play
          :provider provider-id
          :info info}))

(defn set-volume! [volume-level]
  (send! (ensure-launched!)
         {:type :set-volume
          :level volume-level}))

(defn unpause! []
  (send! (ensure-launched!)
         {:type :unpause}))

(comment
  (.kill (:process @state))

  (pause!)
  (unpause!)

  (set-volume! 0.5)

  (play! :ytm {:id "8FV4gcs-MNA"}))
