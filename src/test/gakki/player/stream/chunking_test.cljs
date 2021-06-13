(ns gakki.player.stream.chunking-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            ["stream" :refer [PassThrough]]
            [gakki.player.stream.chunking :as chunking]))

(deftest chunking-test
  (testing "Collects up to chunk size"
    (let [input (PassThrough.)
          chunked (chunking/nbytes input 10)
          emitted (atom [])]
      (.on chunked "data" (partial swap! emitted conj))

      ; Nothing yet:
      (.write input (js/Buffer.alloc 5))
      (is (empty? @emitted))

      ; Now, we emit 10 bytes
      (.write input (js/Buffer.alloc 7))
      (is (= 1 (count @emitted)))
      (is (= 10 (.-length (first @emitted))))

      ; There's 2 bytes still buffered, so writing another 7 does nothing
      (.write input (js/Buffer.alloc 7))
      (is (= 1 (count @emitted)))

      ; Writing 1 fills the buffer precisely
      (.write input (js/Buffer.alloc 1))
      (is (= 2 (count @emitted)))
      (is (= 10 (.-length (second @emitted)))))))

