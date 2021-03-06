(ns gakki.native.macos
  (:require [archetype.util :refer [>evt]]
            ["child_process" :refer [ChildProcess spawn]]
            [clojure.string :as str]
            [cognitect.transit :as t]
            ["path" :rename {join path-join}]
            [promesa.core :as p]
            ["stream" :refer [Readable]]
            [gakki.player :as player]
            [gakki.util.logging :as log]))

(def ^:private native-exe-path
  "./macos/gakki/build/Release/gakki.app/Contents/MacOS/gakki")

(defonce ^:private process (atom nil))
(defonce ^:private requests (atom nil))

; ======= incoming event handling =========================

(defn- process-message [{kind :type :as message}]
  (case kind
    :media (-> message
               (update :event keyword))

    ; pass through
    message))

(defn- parse-message [raw-message]
  (try
    (-> raw-message
        (js/JSON.parse)
        (js->clj :keywordize-keys true)
        (update :type keyword)
        process-message)
    (catch :default e
      (log/error :native
                 "Failed to parse: "
                 raw-message
                 e))))

(defn- handle-media-event [{:keys [event] :as ev}]
  (case event
    :toggle (>evt [:player/play-pause])
    :next-track (>evt [:player/next-in-queue])
    :previous-track (>evt [:player/rewind-or-prev-in-queue])
    :pause (>evt [:player/pause])
    :play (>evt [:player/play])
    :seek (cond
            ; NOTE: macOS times/intervals are all in seconds
            (:relative ev) (>evt [:player/seek-by (:relative ev)])
            (:time ev) (>evt [:player/seek-to (:time ev)])
            :else (log/error :native "Unexpected seek mechanism: " ev))

    (log/error :native "TODO: handle media event: " event)))

(defn- handle-message [{kind :type :as message}]
  (case kind
    :ready nil ; ignore
    :log ((log/of :native) (:message message))
    :media (handle-media-event message)

    :default-device-changed (>evt [:player/check-output-device])
    :default-device-updated (>evt [:player/check-output-device])

    :auth-result (swap! requests update :auth
                        (fn on-auth [handler]
                          (when handler
                            (handler (:auth message)))
                          nil))

    (log/error "Unhandled native event: " message)))

(defn- observe-events [^ChildProcess proc]
  (doto ^Readable (.-stdout proc)
    (.on "data"
         (fn [data]
           (let [parts (str/split (.toString data) "\n")
                 messages (keep parse-message parts)]
             (doseq [message messages]
               (try
                 (handle-message message)
                 (catch :default e
                   (log/error :native "Failed to handle " (str message) e)))))))))

(defn- init-native []
  (let [full-path (path-join js/__dirname
                             ".."
                             native-exe-path)]

    (doto (spawn full-path)
      (.on "error" #(log/error :native "Failed to launch native components" %))
      observe-events)))


; ======= outgoing message handling =======================

(defn- send! [message]
  (when-let [^ChildProcess proc @process]
    (let [message (-> message clj->js js/JSON.stringify)]
      (try (doto (.-stdin proc)
             (.write message)
             (.write "\n"))
           (catch :default e
             (log/error :native
                        (ex-info "Failed to write native message"
                                 {:message message}
                                 e)))))))


; ======= public interface ================================

(defn init []
  (swap! process (fn launch-native [^ChildProcess old]
                   (when old
                     (.kill old))
                   (init-native))))

(defn add-account [account auth]
  (send! {:type :add-account
          :name account
          :value (-> (t/writer :json)
                     (t/write auth))}))

(defn delete-account [account]
  (send! {:type :delete-account
          :name account}))

(defn load-accounts []
  (p/create
    (fn auth [p-resolve]
      (swap! requests assoc :auth
             (fn on-auth [raw-map]
               (p-resolve
                 (->> raw-map
                      (reduce-kv
                        (fn [m account password]
                          (assoc m (keyword account)
                                 (-> (t/reader :json)
                                     (t/read password))))
                        {})))))
      (send! {:type :load-accounts}))))

(defn set-state! [state]
  (send! {:type :set-state
          :current-time (player/current-time)
          :state state}))

(defn set-now-playing!
  "Expects:

     {:title 'title'
      :artist 'artist name'
      :duration duration-seconds
      :image-url 'optional url'}"
  [now-playing]
  (log/debug "set-now-playing!" now-playing)
  (send! (-> now-playing
             (assoc :type :set-now-playing)))
  (set-state! :playing))


; ======= Map containing all optional actions =============

(def commands
  {:init init

   :add-account add-account
   :load-accounts load-accounts
   :delete-account delete-account

   :set-now-playing! set-now-playing!
   :set-state! set-state!})

(comment
  (init)

  (swap! process (fn [^js old]
                   (.kill old "SIGKILL")
                   nil))

  )
