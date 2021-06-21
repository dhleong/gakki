(ns gakki.util.bytesize-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [gakki.util.bytesize :refer [parse]]))

(deftest parse-test
  (testing "Detect unit correctly"
    (is (= 1024 (parse "1KB")))
    (is (= (* 1024 1024) (parse "1Mb")))
    (is (= (* 1024 1024 1024) (parse "1gb")))
    (is (= (* 1024 1024 1024 1024) (parse "1tB"))))

  (testing "Pass through integers unchanged"
    (is (= 1024 (parse 1024))))

  (testing "Larger numbers"
    (is (= (* 1024 1000) (parse "1000kb")))
    (is (= (* 1024 1000) (parse "1,000kb"))))

  (testing "No negative numbers"
    (is (= (* 1024 1000) (parse "-1000kb"))))

  (testing "No fractional support; separators are ignored to be locale-agnostic"
    (is (= (* 1024 1024 1024 5) (parse ".5g")))
    (is (= (* 1024 1024 1024 5000) (parse "5.000g"))))

  (testing "Catch invalid input with default"
    (is (= 42 (parse "g" 42)))
    (is (= 42 (parse "1" 42)))
    (is (= 42 (parse "" 42)))))


