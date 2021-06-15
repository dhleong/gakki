(ns gakki.util.loading
  (:require [archetype.util :refer [>evt]]
            [gakki.util.logging :refer [with-timing-promise]]
            [promesa.core :as p]))

(defn with-loading-promise
  "Wrap a promise to show the loading spinner while it's active and hide it
   when it resolves or rejects.

   Since it's a common pattern, an event name (keyword) may optionally be
   passed to also time the promise (see with-timing-promise). The ordering
   of the arguments does not matter; we will handle any order to facilitate
   `->` threading or simple wrapping"
  ([event promise-obj]
   (if (p/promise? promise-obj)
     ; Normal case:
     (with-timing-promise event
       (with-loading-promise promise-obj))

     ; -> threading
     (with-timing-promise promise-obj
       (with-loading-promise event))))
  ([promise-obj]
   (-> (p/let [_ (>evt [:loading/update-count inc])
               result promise-obj]
         result)
       (p/finally (fn []
                    (>evt [:loading/update-count dec]))))))
