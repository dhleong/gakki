(ns gakki.cli.input-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [gakki.cli.input :refer [apply-help-map]]))

(deftest apply-help-map-test
  (testing "Don't overwrite an existing header"
    (is (= ["source" [{:header "First"}
                      {:header "Second"}]]
           (apply-help-map
             ["source" [{:header "First"}]]
             ""
             {:header "Second"}))))

  (testing ":header-less items create a new section"
    (is (= ["source" [{:header "First"}
                      {"key" "description"}]]
           (apply-help-map
             ["source" [{:header "First"}]]
             ""
             {"key" "description"})))))

