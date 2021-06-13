(ns gakki.util.logging-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [gakki.util.logging :as log]))

(deftest compile-config-test
  (testing "Default to disabled"
    (let [enabled? (log/compile-config "")]
      (is (false? (enabled? "gakki:fighter")))
      (is (false? (enabled? "gakki:player")))))

  (testing "Explicit tag enabling"
    (let [enabled? (log/compile-config "gakki:player")]
      (is (false? (enabled? "gakki:fighter")))
      (is (true? (enabled? "gakki:player")))))

  (testing "Enable-all"
    (let [enabled? (log/compile-config "*")]
      (is (true? (enabled? "gakki")))
      (is (true? (enabled? "serenity")))
      (is (true? (enabled? "serenity:foo")))
      (is (true? (enabled? "gakki:")))
      (is (true? (enabled? "gakki:player")))
      (is (true? (enabled? "gakki:fighter")))))

  (testing "Wildcards enabling"
    (let [enabled? (log/compile-config "gakki:*")]
      (is (false? (enabled? "gakki")))
      (is (false? (enabled? "serenity")))
      (is (false? (enabled? "serenity:foo")))
      (is (true? (enabled? "gakki:")))
      (is (true? (enabled? "gakki:player")))
      (is (true? (enabled? "gakki:fighter")))))

  (testing "Explicit tag disabling"
    (let [enabled? (log/compile-config "-gakki:player")]
      (is (false? (enabled? "gakki:fighter")))
      (is (false? (enabled? "gakki:player")))))

  (testing "Override wildcard to disable"
    (let [enabled? (log/compile-config "*,-gakki:player")]
      (is (true? (enabled? "gakki:fighter")))
      (is (false? (enabled? "gakki:player"))))

    (let [enabled? (log/compile-config "gakki:*,-gakki:player")]
      (is (true? (enabled? "gakki:fighter")))
      (is (false? (enabled? "gakki:player"))))))

