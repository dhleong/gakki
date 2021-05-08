(ns gakki.views
  (:require [archetype.util :refer [<sub]]
            [gakki.views.auth :as auth]))

(def ^:private pages
  {; :home #'home/view
   :auth #'auth/view
   })

(defn main []
  (let [accounts (<sub [:accounts])
        [page args] (<sub [:page])
        page-form [(get pages page) args]]
    [auth/view]
    #_(if accounts
      page-form

      [auth/view])))
