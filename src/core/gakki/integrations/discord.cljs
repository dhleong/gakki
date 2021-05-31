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

(defonce ^:private client-state (atom nil))

(defn- try-connect [^DiscordClient client]
  (-> (p/let [result (.login client
                             #js {:clientId const/discord-app-id
                                  :clientSecret const/discord-oauth-secret
                                  :redirectUri redirect-uri
                                  :scopes scopes})]
        (log/debug "Discord logged in: " result)
        (swap! client-state assoc :ready? true)

        (.subscribe client "VOICE_CONNECTION_STATUS"
                    (fn on-voice-state [ev]
                      (on-voice-connect-status-update ev))))

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

(defn set-state! [{:keys [item state]}]
  (when (and client (:ready? @client-state))
    (-> client
        (.setActivity
          #js {:details (str "Listening to " (:title item))
               :state (str "by " (:artist item)
                           (when (= :paused state)
                             " [paused]"))
               :startTimestamp (when (= :playing state)
                                 (js/Date.now))

               :instance false})
        (p/catch (fn [e]
                   (log/debug "Failed to set discord status" e))))))

#_:clj-kondo/ignore
(comment

  (set-now-playing!
    @(re-frame.core/subscribe [:player/item]))

  (p/let [resp (set-now-playing!
                {:title "Test"
                 :artist "Foo"})]
    (println resp))

  )
