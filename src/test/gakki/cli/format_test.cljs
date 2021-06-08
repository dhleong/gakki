(ns gakki.cli.format-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            ["ink" :as k]
            [gakki.cli.format :refer [hiccup]]))

(deftest format-hiccup-test
  (testing "Simple primitives"
    (is (= [:> k/Text "mreynolds"]
           (hiccup "mreynolds")))
    (is (= [:> k/Text 42]
           (hiccup 42))))

  (testing "Sequence with formatting"
    (is (= [:<>
            [:> k/Text {:bold true} "Kaylee"]
            [:> k/Text "Frye"]]
           (hiccup (list
                     [:b "Kaylee"]
                     "Frye")))))

  (testing "Nested formatting"
    (is (= [:> k/Text {:bold true} "Kaylee"
            [:> k/Text {:underline true} "Frye"]]
           (hiccup [:b "Kaylee" [:u "Frye"]])))))
