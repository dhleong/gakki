(ns gakki.util.convert)

(defn ->float [v]
  (js/parseFloat v 10))

(defn ->int [v]
  (js/parseInt v 10))
