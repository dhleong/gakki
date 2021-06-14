(ns gakki.util.logging
  (:require ["chalk" :as chalk]
            [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [promesa.core :as p]
            [re-frame.core :as re-frame]
            [gakki.const :as const]))

(declare compile-config)

(def ^:private config-enables?
  (delay (compile-config (or js/process.env.DEBUG ""))))

(defn enabled? [tag]
  (or (@config-enables? tag)

      ; The core (debug) method is always enabled for debug builds
      (and const/debug? (= "gakki" tag))))

; ======= Log factories ===================================

(defn- colorizer [^String tag]
  (let [h (mod (hash tag) 360)
        s 40
        v 100]
    (chalk/hsv h s v)))

(def of
  (memoize
    (fn log-creator [tag]
      (let [string-tag (if (string? tag)
                         tag
                         (str/replace (str "gakki" tag) #"[./]" ":"))
            colorized-tag ((colorizer string-tag) string-tag)]
        (fn log [& args]
          (when (enabled? string-tag)
            (apply println colorized-tag args)))))))

(def debug (of nil))
(def player (of :player))


; ======= Error logging ===================================

(defn error? [e]
  (instance? js/Error e))

(defn error [& message]
  (let [tag (if (keyword? (first message))
              (first message)
              nil)
        message (if tag
                  (next message)
                  message)
        ex (some #(when (error? %) %) message)
        without-ex (remove error? message)

        last-map (when (map? (last without-ex))
                   (last without-ex))
        without-ex (if (some? last-map)
                  (butlast without-ex)
                  without-ex)]

    ; NOTE: Error logs cannot be disabled
    (apply println ((chalk/inverse.hsv 0 40 100)
                    (str " ERROR" tag " "))
           (if-some [m (ex-message ex)]
             (concat without-ex [m])
             without-ex))
    (when (some? last-map)
      (pprint last-map))
    (when-let [data (ex-data ex)]
      (pprint data))
    (when-let [stack (when ex (.-stack ex))]
      (println (chalk/gray stack)))))


; ======= Timing ==========================================

(def ^:private colorize-timing (colorizer ":timing"))

(defn timing [event ms]
  ((of :timing) "[" (colorize-timing event) "]: " ms "ms"))

(defn with-timing-promise [event promise-obj]
  (p/let [start (js/Date.now)
          result promise-obj]
    (timing event (- (js/Date.now) start))
    result))


; ======= stdout patching =================================

(defn patch
  "Patch various logging methods to avoid messing up the CLI UI"
  []
  ; stop re-frame loggers from trashing our cli UI
  (let [log (fn [& _]
              ; this is a nop, for now
              )
        console-error (atom js/console.error)
        safe-error (fn safe-error [& args]
                     (when-not (and (string? (first args))
                                    (str/includes?
                                      (first args)
                                      "unmounted component"))
                       (apply @console-error args)))]
    (re-frame/set-loggers!
      {:log      (partial log :info)
       :warn     (partial log :warn)
       :error    (partial debug :error)
       :debug    (partial log :debug)
       :group    (partial log :info)
       :groupEnd  #()})

    ; Even in a prod build, react whines in some situations about a state
    ; update against an unmounted component, but Reagent does seem to clean
    ; up properly so... just suppress the warning.
    (js/Object.defineProperties
      js/console
      #js {:error #js {:get (constantly safe-error)
                       :enumerable true
                       :set (fn [replacement]
                              (when replacement
                                (reset! console-error replacement)))}})))



; ======= debug-style enable/disable configs ==============
; See logging-test for some examples

(defn- compile-check [check]
  (let [raw-regex (-> check
                      (str/replace "*" ".*?"))
        regex (re-pattern (str "^" raw-regex "$"))]
    (fn compiled-check [tag]
      (when (re-matches regex tag)
        true))))

(defn- compile-config-part [part]
  (if (= \- (first part))
    (let [to-invert (compile-check (subs part 1))]
      #(when (true? (to-invert %))
         false))
    (compile-check part)))

(defn- enabled-checks-pass? [checks tag]
  (loop [checks checks
         enabled nil]
    (if-let [check (first checks)]
      (recur (next checks)
             (if-some [result (check tag)]
               ; Subsequent rules overwrite earlier rules
               result
               enabled))

      ; No more checks; return the last-computed state;
      ; "unknown" via nil is coerced to "false"
      (true? enabled))))

(defn compile-config [^String config]
  (if (= "*" config)
    ; easy case:
    (constantly true)

    (let [checks (->> (str/split config #"\s*,\s*")
                      (map compile-config-part))]
      (memoize
        (partial enabled-checks-pass? checks)))))
