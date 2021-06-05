(ns gakki.player.remote
  "Remote-control for the player subprocess"
  (:require [archetype.util :refer [>evt]]
            ["child_process" :refer [fork]]
            [clojure.string :as str]
            [cognitect.transit :as t]
            [gakki.const :as const]
            [gakki.util.logging :as log]))

(def ^:private writer (t/writer :json))
(def ^:private reader (t/reader :json))
(def ^:private debug-io? true)

(defonce state (atom nil))

(defn- send! [state-obj message]
  (->> message
       (t/write writer)
       (.send (:process state-obj))))

(defn- ensure-launched! []
  (if-let [existing @state]
    existing

    (let [proc (fork "resources/player.js"
                     #js {:stdio (if (and debug-io? const/debug?)
                                   "pipe"
                                   "ignore")})
          clear-state! (fn [e]
                         (println "Player Exit:" e)
                         (reset! state nil))]

      (when debug-io?
        (.on (.-stdout proc) "data"
             (fn [stdout]
               (log/debug "[player] " (str/trim (.toString stdout))))))

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

(defn prepare! [provider-id info]
  (send! (ensure-launched!)
         {:type :prepare
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
  (swap! state (fn [{:keys [process]}]
                 (when process
                   (.kill process))
                 nil))

  (pause!)
  (unpause!)

  (set-volume! 0.5)

  (play! :ytm {:id "8FV4gcs-MNA"}))
