(ns gakki.player.track
  (:require ["events" :refer [EventEmitter]]
            [gakki.util.logging :as log]
            [gakki.player.clip :as clip]
            [gakki.player.pcm.core :as pcm :refer [IPCMSource]]
            [gakki.player.track.core :as track]
            [gakki.player.track.events :as events]))

(defn create-with-events [^IPCMSource pcm-source, ^EventEmitter events]
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
                   (log/debug "Closing old " old)
                   (close old))

                 (doto
                   (create
                     (gakki.player.pcm/create-caching-source
                       "ytm.2mqi6Vqfhh8"
                       #(gakki.player.ytm/youtube-id->stream "2mqi6Vqfhh8")))
                   (clip/play))))


  (close @test-track)

  (clip/play @test-track)
  (clip/pause @test-track)
  (seek @test-track 20)

  )
