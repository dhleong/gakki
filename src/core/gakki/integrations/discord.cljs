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

(declare set-state!)

; ======= events ==========================================

(defn- on-voice-connect-status-update [ev]
  (when-not (= false (:suppress-in-voice? (:config @client-state)))
    (case (j/get ev :state)
      "DISCONNECTED" (>evt [:integrations/set-remove :voice-connected :discord])
      "VOICE_CONNECTED" (>evt [:integrations/set-add :voice-connected :discord])

      ; ignore:
      nil)))


; ======= connection management / init ====================

(defn- disconnect [{:keys [connecting?]}]
  (swap! client-state
         (fn [{:keys [^DiscordClient client, retry-timeout last-state]}]
           (when client
             (.removeAllListeners client "disconnected")
             (.destroy client))
           (when retry-timeout
             (js/clearTimeout retry-timeout))
           {:last-state last-state
            :connecting? connecting?})))

(declare ^:private retry-connect)

(defn- try-connect []
  (disconnect {:connecting? true})

  (-> (p/let [^DiscordClient client (DiscordClient. #js {:transport "ipc"})
              result (.login client
                             #js {:clientId const/discord-app-id
                                  :clientSecret const/discord-oauth-secret
                                  :prompt "none"
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
                        (on-voice-connect-status-update ev))))

        (when-let [last-state (:last-state @client-state)]
          (set-state! last-state)))

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


; ======= public interface ================================

(defn set-state! [{:keys [item state] :as full-state}]
  (let [{:keys [config]} (swap! client-state assoc :last-state full-state)]
    (when-not (= false (:share-activity? config))
      (when-let [^DiscordClient client (:client @client-state)]
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
                       (log/debug "Failed to set discord status" e))))))))

(defn configure!
  "Expects a map:
    {:share-activity? bool  ; if `false`, disables activity sharing
     :suppress-in-voice? bool ; if `false`, disables volume suppression in voice
     }"
  [config]
  (let [state (swap! client-state assoc :config config)
        ^DiscordClient client (:client state)]
    (cond
      (nil? config) (disconnect {:connecting? false})

      (not (or client
               (:retry-timeout state)
               (:connecting? state)))
      (try-connect)

      ; reconnecting:
      (not client) nil

      ;; already connected; reconfigure:

      :else
      (do
        (if (false? (:share-activity? config))
          (.clearActivity client)
          (set-state! (:last-state state)))

        (when (false? (:suppress-in-voice? config))
          (>evt [:integrations/set-remove :voice-connected :discord]))))))

#_:clj-kondo/ignore
(comment

  (set-now-playing!
    @(re-frame.core/subscribe [:player/item]))

  (p/let [resp (set-now-playing!
                {:title "Test"
                 :artist "Foo"})]
    (println resp))

  )
