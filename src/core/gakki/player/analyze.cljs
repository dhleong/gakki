(ns gakki.player.analyze
  (:require ["child_process" :refer [exec]]
            [clojure.string :as str]
            ["ffmpeg-static" :as ffmpeg-path]
            [promesa.core :as p]
            [gakki.util.convert :refer [->int]]))

(def duration-regex #"Duration: (\d+):(\d+):(\d+).(\d+)")
(def sample-rate-regex #"(\d+) Hz")
(def stereo-mono-regex #"(stereo|mono)")

(defn analyze-audio [path]
  (p/create
    (fn [p-resolve p-reject]
      (exec (->> [ffmpeg-path "-i" (str "\"" path "\"")]
                 (str/join " "))

            #js {:windowsHide true}
            (fn calback [err _stdout stderr]
              (let [[_ h m s fraction] (re-find duration-regex stderr)
                    [_ sample-rate] (re-find sample-rate-regex stderr)
                    [_ stereo-mono] (re-find stereo-mono-regex stderr)]
                (if sample-rate
                  (p-resolve
                    {:duration (+ (* (->int h) 3600 1000)
                                  (* (->int m) 60000)
                                  (* (->int s) 1000)
                                  (* (/ (->int fraction) 100.0) 1000))
                     :sample-rate (->int sample-rate)
                     :channels (if (= "stereo" stereo-mono)
                                 2
                                 1)

                     ; TODO extract these:
                     :codec "opus"
                     :container "webm"
                     :frame-size 960
                     })

                  (p-reject (or err
                                (ex-info "Failed to extra audio info"
                                         {:out stderr}))))))))))

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

  (p/then
    (analyze-audio
      (str js/process.env.HOME
           "/Library/Caches/gakki/ytm.0LZb6EeCdO0"))
    println)

  )
