(ns gakki.views.search
  (:require [archetype.util :refer [>evt <sub]]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            ["ink-text-input" :default TextInput]
            [promesa.core :as p]
            ["react" :rename {useEffect use-effect
                              useState use-state}]
            [reagent.core :as r]
            [gakki.accounts :as accounts]
            [gakki.cli.format :as format]
            [gakki.cli.input :refer [use-input]]
            [gakki.components.frame :refer [frame]]
            [gakki.components.header :refer [header]]
            [gakki.components.scrollable :refer [vertical-list]]
            [gakki.util.functional :refer [length-wrapped]]))

(defn- use-debounced
  ([f args] (use-debounced f 350 args))
  ([f debounce-delay args]
   (use-effect
     #(let [timeout (js/setTimeout f debounce-delay)]
       (partial js/clearTimeout timeout))
     args)))

(defn- use-suggestions [accounts query]
  (let [[suggestions set-suggestions!] (use-state nil)
        [default-suggestions set-default!] (use-state nil)]

    (use-effect
      #(set-suggestions! (when (empty? query)
                           default-suggestions))
      #js [query])

    (use-debounced
      #(p/let [results (accounts/search-suggest accounts query)]
         (when (empty? query)
           (set-default! results))
         (set-suggestions! results))
      #js [accounts query])

    suggestions))

(defn- suggestion-row [{:keys [selected?] hiccup :formatted}]
  [:> k/Box {:flex-direction :row}
   [:> k/Text {:inverse selected?}
    (format/hiccup hiccup)]])

(defn view []
  ; NOTE: use-state seems *slightly* faster here than using a ratom for typing
  (r/with-let [selected-index (r/atom nil)]
    (let [[input set-input!] (use-state "")
          suggestions (use-suggestions
                        (<sub [:accounts])
                        input)
          suggestions (if-some [idx @selected-index]
                        (assoc-in suggestions [idx :selected?] true)
                        suggestions)]
      (use-input
        (fn search-input [k]
          (case k
            :escape (cond
                      (some? @selected-index)
                      (reset! selected-index nil)

                      (empty? input)
                      (>evt [:navigate/back!])

                      :else
                      (set-input! ""))
            :return (let [query (if-some [idx @selected-index]
                                  (:query (nth suggestions idx))
                                  input)]
                      (when-not (empty? query)
                        (>evt [:search/reload! query])
                        (>evt [:navigate! [:search/results query]])))
            :tab (when (seq suggestions)
                   (swap! selected-index (length-wrapped
                                           (fnil inc -1)
                                           (count suggestions))))

            ; else, go back to typing
            (reset! selected-index nil))))

      [frame
       [header {:padding-bottom 1} "Search"]
       [:> k/Box {:height 1
                  :border-bottom :round}
        [:> TextInput {:on-change set-input!
                       :placeholder "Search for something"
                       :show-cursor (nil? @selected-index)
                       :value input}]]
       (if (nil? suggestions)
         [:> Spinner {:type "dots"}]

         [vertical-list
          :items suggestions
          :key-fn :query
          :render suggestion-row])])))
