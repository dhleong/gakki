(ns gakki.components.figure
  (:require
   ["figures" :default figures]
   ["ink" :as k]
   [clojure.string :as str]))

(def ^:private kind->name
  (memoize
   (fn [k]
     (let [parts (-> (name k)
                     (str/split #"-"))]
       (->> (rest parts)
            (map str/capitalize)
            (cons
             (first parts))
            (str/join))))))

(defn figure [kind]
  [:> k/Text (case kind
               :music "♫"
               (aget figures (kind->name kind)))])
