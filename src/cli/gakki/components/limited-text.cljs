(ns gakki.components.limited-text
  (:require [applied-science.js-interop :as j]
            [ink :as k]
            ["react" :as react]))

(defn- limiter [{:keys [max-width] :as opts} & children]
  (let [rf (react/useRef)
        [measured-width set-measured-width!] (react/useState)]
    (react/useEffect
      (fn []
        (when-let [node (.-current rf)]
          (j/let [^:js {:keys [width]} (k/measureElement node)]
            (set-measured-width! width)))))

    [:> k/Box {:ref rf
               :width (when measured-width
                        (min max-width measured-width))}
     (into [:> k/Text opts] children)]))

(defn limited-text [{:keys [_max-width] :as opts} & children]
  (let [opts (update opts :wrap (fn [provided]
                                  (if (nil? provided)
                                    :truncate-end
                                    provided)))]
    (into [:f> limiter opts] children)))
