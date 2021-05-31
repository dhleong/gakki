(ns gakki.integrations.discord
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            ["discord-rpc" :rename {Client DiscordClient}]
            [promesa.core :as p]
            [gakki.const :as const]
            [gakki.util.logging :as log]))

(def ^:private scopes #js ["rpc" "rpc.voice.read"])
(def ^:private redirect-uri "http://127.0.0.1")

(declare ^:private on-voice-connect-status-update)

(defn- try-connect [^DiscordClient client]
  (-> (p/let [result (.login client
                             #js {:clientId const/discord-app-id
                                  :clientSecret const/discord-oauth-secret
                                  :redirectUri redirect-uri
                                  :scopes scopes})]
        (log/debug "Discord logged in: " result)

        (.subscribe client "VOICE_CONNECTION_STATUS"
                    (fn on-voice-state [ev]
                      (on-voice-connect-status-update ev)))

        )

      (p/catch (j/fn [^:js {:keys [code] :as e}]
                 (if (not= "ECONNREFUSED" code)
                   (do (println "Error connecting to Discord:" e)
                       (js/console.log e))
                   (println "TODO retry connection"))))))

(defonce ^:private client
  (when-not (empty? const/discord-oauth-secret)
    (doto (DiscordClient. #js {:transport "ipc"})
      (.on "error" (fn [e] (println "RPC Client error" e)))
      (try-connect))))


; ======= events ==========================================

(defn- on-voice-connect-status-update [ev]
  (case (j/get ev :state)
    "DISCONNECTED" (>evt [:integrations/set-remove :voice-connected :discord])
    "VOICE_CONNECTED" (>evt [:integrations/set-add :voice-connected :discord])

    ; ignore:
    nil))

; ======= public interface ================================

(defn set-now-playing! [item]
  (when client
    (-> client
        (.setActivity
          #js {:details "listening to music"
               :startTimestamp (js/Date.now)
               :state (str "listening to " (:title item))
               :type 2
               :instance false}))))

(comment

  (p/let [resp (set-now-playing!
                {:title "Test"})]
    (println resp))

  )
