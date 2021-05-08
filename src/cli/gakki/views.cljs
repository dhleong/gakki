(ns gakki.views
  (:require [archetype.util :refer [<sub]]
            [gakki.views.auth :as auth]
            [gakki.views.home :as home]))

(def ^:private pages
  {:home #'home/view
   :auth #'auth/view
   })

(defn main []
  (let [accounts (<sub [:accounts])
        [page args] (<sub [:page])
        page-form [:f> (get pages page) args]]
    (if accounts
      page-form

      [:f> auth/view])))
