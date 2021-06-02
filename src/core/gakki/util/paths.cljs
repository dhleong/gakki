(ns gakki.util.paths
  (:require [applied-science.js-interop :as j]
            ["env-paths" :as env-paths]
            ["os" :as os]
            ["path" :as path]))

(defn platform [kind]
  (-> (env-paths "gakki", #js {:suffix ""})
      (j/get kind)))

(defn user-config
  ([] (path/join
        (os/homedir)
        ".config"
        "gakki"))
  ([sub-path]
   (path/join (user-config) sub-path)))

(defn internal-data
  ([] (platform :data))
  ([sub-path]
   (path/join (internal-data) sub-path)))
