(ns gakki.cli.format
  (:require [clojure.core.match :refer [match]]
            ["ink" :as k]))

(declare hiccup)

(defn- primitive? [v]
  (or (string? v) (number? v)))

(defn- primitive-or-hiccup [v]
  (if (primitive? v)
    v
    (hiccup v)))

(defn- into-text-with [props children]
  (let [children (map primitive-or-hiccup children)]
    (into [:> k/Text props] children)))

(defn- ->hiccup [form]
  (match form
    [:b & bold] (into-text-with {:bold true} bold)
    [:i & bold] (into-text-with {:italic true} bold)
    [:u & bold] (into-text-with {:underline true} bold)

    (s :guard primitive?) [:> k/Text s]
    _ [:> k/Text (str "UNEXPECTED: " (js/JSON.stringify form) form)]))

(defn hiccup [input]
  (cond
    (primitive? input) (->hiccup input)
    (vector? input) (->hiccup input)
    :else (->> input
               (map ->hiccup)
               (into [:<>]))))
