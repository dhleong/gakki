(ns gakki.player.cache-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [gakki.player.cache :as cache]))

(deftest cache-state-test
  (testing "LRU eviction"
    (let [evicted (atom [])
          c (#'cache/create-state
              5 (partial swap! evicted conj))]
      (swap! c #'cache/put-with-size "four" 4)
      (swap! c #'cache/put-with-size "one" 1)
      (swap! c #'cache/put-with-size "four" 4)
      (is (empty? @evicted))

      (swap! c #'cache/put-with-size "new" 1)
      (is (= ["one"] @evicted))

      (swap! c #'cache/put-with-size "newest" 5)
      (is (= ["one" "four" "new"] @evicted)))))

(deftest state-init-test
  (testing "Initialize state"
    (let [evicted (atom [])
          c (#'cache/create-state
              5 (partial swap! evicted conj))]
      (swap! c cache/initialize-state-with-stats
             [["old" #js {:size 1 :atimeMs 1}]
              ["newest" #js {:size 1 :atimeMs 5}]
              ["oldest" #js {:size 1 :atimeMs 0}]
              ["newer" #js {:size 1 :atimeMs 3}]
              ["new" #js {:size 1 :atimeMs 2}]])
      (is (empty? @evicted))

      (swap! c #'cache/put-with-size "evict-oldest" 1)
      (is (= ["oldest"] @evicted))

      (swap! c #'cache/put-with-size "evict-old" 1)
      (is (= ["oldest" "old"] @evicted))

      (swap! c #'cache/put-with-size "evict-new" 1)
      (is (= ["oldest" "old" "new"] @evicted)))))
