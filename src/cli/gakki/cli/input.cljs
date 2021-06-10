(ns gakki.cli.input
  (:require ["ink" :as k]
            ["react" :rename {useEffect use-effect}]
            [reagent.impl.component :as reagent-impl]
            [gakki.cli.keys :refer [->key]]
            [gakki.util.coll :refer [vec-dissoc]]))

(defonce ^:private active-stack (atom []))
(defonce ^:private handler (atom nil))

(defn- recompute-handler [stack]
  (reset! handler
          (reduce
            (fn [h {:keys [f name owned]}]
              (cond
                ; fn inputs override all maps
                (nil? owned) f

                ; Map inputs cannot override fn inputs for now
                (fn? h) h

                :else
                (reduce-kv
                  (fn [m k v]
                    (assoc m k [name v]))
                  h
                  owned)))
            {}
            stack)))

(defn- component-name []
  (or (when-let [^js c reagent-impl/*current-component*]
        (when-let [f (.-reagentRender c)]
          (if (var? f)
            (str (:ns (meta f))
                 "/"
                 (:name (meta f)))
            (.-name f))))

      (let [n (reagent-impl/comp-name)]
        (when-not (empty? n)
          n))

      "<unknown>"))

(defn use-input
  "Hook-based input handler that's a super-powered layer atop ink's useInput.
   We register a single handler via the [dispatcher] component which dispatches
   to the most-recent registered input handler, supporting overlapping handlers
   when provided with a map of key -> handler fn."
  [f]
  (let [entry {:f (when (fn? f)
                    f)
               :name (component-name)
               :owned (when (map? f)
                        f)}]

    (use-effect
      (fn []
        (recompute-handler
          (swap! active-stack conj entry))
        #(recompute-handler
           (swap! active-stack vec-dissoc entry)))
      #js [entry])))

(defn dispatcher []
  (k/useInput
    (fn input-dispatcher [input k]
      (let [the-key (->key input k)
            handler @handler]
        (println input k " -> " the-key)
        (if (fn? handler)
          (handler the-key)

          (when-let [[owner f] (get handler the-key)]
            (cond
              (and (fn? f) (= 0 (.-length f)))
              (f)

              :else
              (println "ERROR: Handler to " the-key " was not a zero-arity fn."
                       "\n  Value: " f
                       "\n  Registered: " owner)))))))

  ; This is a functional component that doesn't render anything
  nil)
