(ns gakki.native.macos
  (:require [archetype.util :refer [>evt]]
            ["child_process" :refer [ChildProcess spawn]]
            [clojure.string :as str]
            ["path" :rename {join path-join}]
            ["stream" :refer [Readable]]))

(def ^:private native-exe-path
  "./macos/gakki/build/Release/gakki.app/Contents/MacOS/gakki")

(defonce ^:private process (atom nil))


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
      (println "[err:native] Failed to parse: "
               raw-message "\n" e))))

(defn- handle-media-event [{:keys [event]}]
  (case event
    :toggle (>evt [:player/play-pause])
    :next-track (>evt [:player/next-in-queue])

    (println "TODO: handle media event: " event)))

(defn- handle-message [{kind :type :as message}]
  (case kind
    :ready nil ; ignore
    :log (println "[log:native]" (:message message))
    :media (handle-media-event message)

    (println "Unhandled native event: " message)))

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
                   (println "[err:native] Failed to handle " message
                            "\n" e)))))))))

(defn- init-native []
  (let [full-path (path-join js/__dirname
                             ".."
                             native-exe-path)]

    (doto (spawn full-path)
      (.on "error" #(println "Failed to launch native components" %))
      observe-events)))


; ======= outgoing message handling =======================

(defn- send! [message]
  (when-let [^ChildProcess proc @process]
    (let [message (-> message clj->js js/JSON.stringify)]
      (try (doto (.-stdin proc)
             (.write message)
             (.write "\n"))
           (catch :default e
             (println "ERR: Failed to write native message:" message
                      "\n: " e))))))


; ======= public interface ================================

(defn launch []
  (swap! process (fn launch-native [^ChildProcess old]
                   (when old
                     (.kill old))
                   (init-native))))

(defn set-state! [state]
  (send! {:type :set-state
          :state state}))

(defn set-now-playing!
  "Expects:

     {:title 'title'
      :artist 'artist name'
      :image-url 'optional url'}"
  [now-playing]
  (send! (assoc now-playing
                :type :set-now-playing))
  (set-state! :playing))

(comment
  (launch)

  (swap! process (fn [^js old]
                   (.kill old "SIGKILL")
                   nil))

  )
