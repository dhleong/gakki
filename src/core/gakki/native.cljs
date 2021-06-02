(ns gakki.native
  (:require ["os" :as os]
            [gakki.native.default :as default]
            [gakki.native.macos :as macos]))

(def ^:private platforms
  {"darwin" macos/commands
   :default default/commands})

(def ^:private handler
  (memoize
    (fn handler [k]
      (or (when-let [of-platform (get platforms (os/platform))]
            (get of-platform k))
          (get (:default platforms) k)
          identity))))


; ======= public interface ================================

(defn init []
  ((handler :init)))

(defn add-account [provider-id account]
  ((handler :add-account) provider-id account))

(defn load-accounts []
  ((handler :load-accounts)))

(defn load-prefs []
  ((handler :load-prefs)))

(defn load-persistent-state []
  ((handler :load-persistent-state)))

(defn save-persistent-state [state]
  ((handler :save-persistent-state) state))

(defn set-state! [state]
  ((handler :set-state!) state))

(defn set-now-playing! [now-playing]
  ((handler :set-now-playing!) now-playing))
