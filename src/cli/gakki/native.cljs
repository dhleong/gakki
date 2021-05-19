(ns gakki.native
  (:require ["os" :as os]
            [gakki.native.macos :as macos]))

(defn init []
  (case (os/platform)
    "darwin" (macos/launch)))

(defn set-state! [state]
  (case (os/platform)
    "darwin" (macos/set-state! state)))

(defn set-now-playing! [now-playing]
  (case (os/platform)
    "darwin" (macos/set-now-playing! now-playing)))
