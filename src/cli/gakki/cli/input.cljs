(ns gakki.cli.input
  (:require [archetype.util :refer [>evt]]
            ["ink" :as k]
            ["react" :rename {useEffect use-effect}]
            [reagent.impl.component :as reagent-impl]
            [gakki.cli.keys :refer [->key]]
            [gakki.util.coll :refer [vec-dissoc]]
            [gakki.util.logging :as log]))

(defonce ^:private active-stack (atom []))
(defonce ^:private handler (atom nil))

(defn apply-help-map [existing-help-entry source v]
  (let [stack-top-index (when existing-help-entry
                          (dec (count (second existing-help-entry))))]
    (cond
      ; First :help map
      (not existing-help-entry)
      [source [v]]

      ; New section: inputs are registered in reverse order (with the container
      ; registering last) due to how React renders them
      (get-in existing-help-entry [1 stack-top-index :header])
      (update existing-help-entry 1 conj v)

      ; Merge into existing section
      :else
      (update-in existing-help-entry [1 stack-top-index] merge v))))

(defn- compute-handler [stack]
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
            (if (= :help k)
              ; :help maps are a special case
              (update m k apply-help-map name v)

              ; Normal case:
              (assoc m k [name v])))
          h
          owned)))
    {}
    stack))

(defn- recompute-handler [stack]
  (reset! handler (compute-handler stack)))

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
        (if (fn? handler)
          (handler the-key)

          (if-let [[owner f] (get handler the-key)]
            (cond
              (and (fn? f) (= 0 (.-length f)))
              (f)

              :else
              (log/error "Handler to " the-key " was not a zero-arity fn."
                         "\n  Value: " f
                         "\n  Registered: " owner))

            (when (and (= "?" the-key)
                       (:help handler))
              (>evt [:navigate! [:help (second (:help handler))]])))))))

  ; This is a functional component that doesn't render anything
  nil)
