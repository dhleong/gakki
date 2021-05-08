(ns gakki.util.logging
  (:require [clojure.string :as str]
            [re-frame.core :as re-frame]))

(defn patch
  "Patch various logging methods to avoid messing up the CLI UI"
  []
  ; stop re-frame loggers from trashing our cli UI
  (let [log (fn [& _]
              ; this is a nop, for now
              )
        console-error (atom js/console.error)
        safe-error (fn console-error [& args]
                     (when-not (and (seq args)
                                    (str/includes?
                                      (first args)
                                      "unmounted component"))
                       (apply @console-error args)))]
    (re-frame/set-loggers!
      {:log      (partial log :info)
       :warn     (partial log :warn)
       :error    (partial log :error)
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
                              (reset! console-error replacement))}})))
