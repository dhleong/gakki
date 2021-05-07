(ns gakki.util.convert)

(defn ->int [v]
  (js/parseInt v 10))
