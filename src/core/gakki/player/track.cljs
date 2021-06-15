(ns gakki.player.track
  (:require ["events" :refer [EventEmitter]]
            [gakki.util.logging :as log]
            [gakki.player.clip :as clip]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.track.core :as track]
            [gakki.player.track.events :as events]
            [promesa.core :as p]))

(defn create-with-events [^IPCMSource pcm-source, ^EventEmitter events]
  (-> pcm-source
      (pcm/prepare)
      (p/catch (fn [e]
                 ((log/of :track) "Error preparing " pcm-source ": " e)
                 (.emit events "end"))))
  (events/wrap
    (track/create pcm-source)
    events))

; TODO: We probably want to refactor this out and use AudioTrack directly...
(defn create-playable [^IPCMSource pcm-source]
  (create-with-events pcm-source (EventEmitter.)))

(defonce ^:private test-track (atom nil))

#_:clj-kondo/ignore
(comment

  (swap! test-track (fn [old]
                 (when old
                   ((log/logger :track) "Closing old " old)
                   (track/close old))

                 (doto
                   (gakki.accounts.ytm.playable/from-id nil "2mqi6Vqfhh8")
                   (clip/play))))


  (track/close @test-track)

  (clip/play @test-track)
  (clip/pause @test-track)
  (track/seek @test-track 20)

  )
