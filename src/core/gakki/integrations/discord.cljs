(ns gakki.integrations.discord
  (:require [applied-science.js-interop :as j]
            [archetype.util :refer [>evt]]
            ["discord-rpc" :rename {Client DiscordClient}]
            [promesa.core :as p]
            [gakki.const :as const]
            [gakki.util.logging :as log]))

(def ^:private scopes #js ["rpc" "rpc.voice.read"])
(def ^:private redirect-uri "http://127.0.0.1")
(def ^:private reconnect-delay 5000)

(defonce ^:private client-state (atom nil))

; ======= events ==========================================

(defn- on-voice-connect-status-update [ev]
  (case (j/get ev :state)
    "DISCONNECTED" (>evt [:integrations/set-remove :voice-connected :discord])
    "VOICE_CONNECTED" (>evt [:integrations/set-add :voice-connected :discord])

    ; ignore:
    nil))


; ======= connection management / init ====================

(declare ^:private retry-connect)

(defn- try-connect []
  (swap! client-state
         (fn [{:keys [^DiscordClient client, retry-timeout last-state]}]
           (when client
             (.removeAllListeners client "disconnect")
             (.destroy client))
           (when retry-timeout
             (js/clearTimeout retry-timeout))
           {:last-state last-state
            :connecting? true}))

  (-> (p/let [^DiscordClient client (DiscordClient. #js {:transport "ipc"})
              result (.login client
                             #js {:clientId const/discord-app-id
                                  :clientSecret const/discord-oauth-secret
                                  :redirectUri redirect-uri
                                  :scopes scopes})]
        (log/debug "Discord logged in: " result)
        (swap! client-state assoc
               :connecting? false
               :ready? true
               :client client)

        (doto client
          (.once "disconnected"
                 (fn []
                   (log/debug "Lost connection to discord; reconnecting later")
                   (retry-connect)))

          (.subscribe "VOICE_CONNECTION_STATUS"
                      (fn on-voice-state [ev]
                        (on-voice-connect-status-update ev)))))

      (p/catch (j/fn [^:js {:keys [code message] :as e}]
                 (if-not (or (= "ECONNREFUSED" code)
                             (= "Could not connect" message))
                   (do (println "Error connecting to Discord:" e)
                       (js/console.log e))

                   (retry-connect))))))

(defn- retry-connect []
  (swap! client-state assoc
         :connecting? false
         :client nil
         :retry-timeout (js/setTimeout
                          try-connect
                          reconnect-delay)))

(let [state @client-state]
  (when-not (or (:client state)
                (:retry-timeout state)
                (:connecting? state))
    (try-connect)))


; ======= public interface ================================

(defn set-state! [{:keys [item state] :as full-state}]
  (swap! client-state assoc :last-state full-state)

  (when-let [^DiscordClient client (:state @client-state)]
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
