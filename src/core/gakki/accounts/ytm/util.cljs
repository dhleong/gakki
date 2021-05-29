(ns gakki.accounts.ytm.util
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]))

(defn runs->text [^js runs-container]
  (when-let [runs (j/get runs-container :runs)]
    (->> runs
         (map #(j/get % :text))
         (str/join " "))))

