(ns gakki.player.util
  #? (:cljs (:require ["events" :refer [EventEmitter]]
                      [gakki.util.logging :as log]
                      [promesa.core :as p])))

;; We wait this many milliseconds after the end of the provided body for
;; an error emission from the EventEmitter before deciding that no errors
;; will be emitted.
(def catch-error-delay-ms 50)

#? (:cljs
     (defn run-consuming-error [^EventEmitter emitter, f]
       (p/create
         (fn [p-resolve p-reject]
           (let [result (atom nil)]
             ((log/of :player/util) "listen for errors...")
             (.once emitter "error" p-reject)
             (try
               (reset! result (f))
               (catch :default e
                 (p-reject e))
               (finally
                 (js/setTimeout
                   (fn cleanup-listener []
                     ((log/of :player/util) "stop listening for errors.")
                     (p-resolve @result)
                     (.off emitter "error" p-reject))
                   catch-error-delay-ms))))))))

(defmacro with-consuming-error
  "Given an EventEmitter and a body, returns a promise that runs the `body` in
   an implicit `(do)`, and emits any error thrown or emitted through the
   EventEmitter up to some short delay after the end of `body`. If no error is
   encountered, the promise resolves to result of `body`."
  [emitter-name & body]
  `(run-consuming-error
     ~emitter-name
     (fn [] ~@body)))
