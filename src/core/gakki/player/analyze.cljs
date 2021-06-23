(ns gakki.player.analyze
  (:require [applied-science.js-interop :as j]
            ["child_process" :refer [execFile]]
            [clojure.string :as str]
            ["ffmpeg-static" :as ffmpeg-path]
            [gakki.util.logging :as log]
            [promesa.core :as p]
            [gakki.const :as const]
            [gakki.util.convert :refer [->int]]))

(def duration-regex #"Duration: (\d+):(\d+):(\d+).(\d+)")
(def sample-rate-regex #"(\d+) Hz")
(def stereo-mono-regex #"(stereo|mono)")
(def container-regex #"Input #0, ([^ ]+), from")
(def codec-specific #"Audio: .*?\(([a-z0-9]+) /")
(def codec-simple #"Audio: ([^,]+),")

(def error-message-regex #"(?m)^([^:]+): (.+)$")

(def ^:private accepted-containers #{"webm" "mp4"})

(defn- parse-container [output]
  (let [[_ formats] (re-find container-regex output)]
    (some accepted-containers (str/split formats #","))))

(defn- parse-codec [output]
  (or (when-let [[_ specific] (re-find codec-specific output)]
        specific)
      (when-let [[_ simple] (re-find codec-simple output)]
        simple)))

(defn- parse-ffmpeg [output]
  (let [[_ h m s fraction] (re-find duration-regex output)
        [_ sample-rate] (re-find sample-rate-regex output)
        [_ stereo-mono] (re-find stereo-mono-regex output)
        codec (parse-codec output)]
    (when sample-rate
      {:duration (+ (* (->int h) 3600 1000)
                    (* (->int m) 60000)
                    (* (->int s) 1000)
                    (* (/ (->int fraction) 100.0) 1000))
       :sample-rate (->int sample-rate)
       :channels (if (= "stereo" stereo-mono)
                   2
                   1)

       :codec codec
       :container (parse-container output)

       :frame-size const/default-frame-size})))

(defn- extract-error-message [path output]
  (or (when-let [[_ message] (re-find (re-pattern
                                        (str "(?m)^" path ": (.+)$"))
                                      output)]
        {:path path
         :error message})
      {:unparsed output}))

(defn analyze-audio [path]
  ((log/of :player/analyze) "Analyzing " path "...")
  (p/create
    (fn [p-resolve p-reject]
      (execFile
        ffmpeg-path
        #js ["-i" path]
        #js {:windowsHide true}
        (fn callback [err _stdout stderr]
          ((log/of :player/analyze) "Callback returned.")
          (if-let [parsed (parse-ffmpeg stderr)]
            (p-resolve parsed)
            (p-reject (ex-info "Failed to analyze audio file"
                               {:err {:code (j/get err .-code)
                                      :cmd (j/get err .-cmd)
                                      :signal (j/get err .-signal)}
                                :message (extract-error-message path stderr)}))))))))

(defn audio-caching
  "This is a convenient, in-memory caching wrapper around `analyze-audio` that
   `assoc`'s its results in the given `atom-key` of the given `cache-atom`.

   Multiple simultaneous invocations may result in multiple analyze-audio calls,
   but the first invocation (and all subsequent) after a success will read from
   the cached value in the atom."
  [cache-atom atom-key path]
  (if-let [cached (get @cache-atom atom-key)]
    (p/resolved cached)

    (p/let [analysis (analyze-audio path)]
      (swap! cache-atom assoc atom-key analysis)
      analysis)))

(comment

  (p/handle
    (analyze-audio
      (str js/process.env.HOME
           "/Library/Caches/gakki/ytm.iZcjm4WDdbg"))
    log/error)

  )
