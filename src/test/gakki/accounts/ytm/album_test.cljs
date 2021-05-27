(ns gakki.accounts.ytm.album-test
  (:require [applied-science.js-interop :as j]
            [cljs.test :refer-macros [deftest is testing]]
            [gakki.accounts.ytm.album :refer [apply-mutations]]))

(declare response-mutations)

(deftest apply-mutations-test
  (testing "Index album"
    (let [state (apply-mutations response-mutations)]
      (is (= ["track"]
             (:musicTrack state))))))

(def response-mutations
  (j/lit
    [{:entityKey "track"
      :type "ENTITY_MUTATION_TYPE_REPLACE"
      :payload
      {:musicTrack
       {:id "track",
        :title "Sounds of Serenity",
        :thumbnailDetails
        {:thumbnails [{:url "thumb",
                       :width 60,
                       :height 60}]},
        :artists ["artist"],
        :artistNames "CHVRCHES",
        :videoId "DAE0tpnwGEc",
        :userDetails "user-details",
        :albumRelease "album-release",
        :albumTrackIndex "1",
        :lengthMs "186644",
        :contentRating {:explicitType
                        "MUSIC_ENTITY_EXPLICIT_TYPE_NOT_EXPLICIT"},
        :share "EgtEQUUwdHBud0dFYyBkKAE%3D"}
       }}]))
