(ns gakki.views.search
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            ["ink-text-input" :default TextInput]
            [promesa.core :as p]
            ["react" :rename {useEffect use-effect
                              useState use-state}]
            [gakki.accounts :as accounts]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]))

(defn- use-debounced
  ([f args] (use-debounced f 750 args))
  ([f debounce-delay args]
   (use-effect
     #(let [timeout (js/setTimeout f debounce-delay)]
       (partial js/clearTimeout timeout))
     args)))

(defn- use-suggestions [accounts query]
  (let [[suggestions set-suggestions!] (use-state nil)]

    (use-debounced
      #(p/let [results (accounts/search-suggest accounts query)]
         (set-suggestions! results))
      #js [accounts query])

    suggestions))

(defn view []
  ; NOTE: use-state seems *slightly* faster here than using a ratom for typing
  (let [[input set-input!] (use-state "")
        suggestions (use-suggestions (<sub [:accounts]) input)]
    (use-input
      (fn [k]
        (case k
          :escape (if (empty? input)
                    (>evt [:navigate/back!])
                    (set-input! ""))
          nil)))

    [frame
     [header "Search"]
     [:> TextInput {:value input :on-change set-input!}]
     [:> k/Text (str (first suggestions))]]))
