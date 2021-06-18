(ns gakki.util.bytesize
  (:require [clojure.string :as str]))

(def ^:private byte-size-pattern #"([0-9,.]+)\s*([kmgt])")
(def ^:private unit-scale
  {"k" 10
   "m" 20
   "g" 30
   "t" 40})

(defn parse
  ([s default] (try (parse s)
                    (catch :default _
                      default)))
  ([s]
   (if (number? s)
     ; Simple case: pass through numbers unchanged
     s

     (if-let [[_ raw-number unit] (->> (str/replace s #"[,.]" "")
                                       (str/lower-case)
                                       (re-find byte-size-pattern))]
       (let [number (js/parseInt raw-number 10)
             scale (get unit-scale unit 1)]
         (* number (js/Math.pow 2 scale)))

       (throw (ex-info "Invalid byte-size description" {:input s}))))))
