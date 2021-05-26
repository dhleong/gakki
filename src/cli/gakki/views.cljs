(ns gakki.views
  (:require [archetype.util :refer [<sub]]
            [gakki.views.auth :as auth]
            [gakki.views.auth.ytm :as auth-ytm]
            [gakki.views.album :as album]
            [gakki.views.home :as home]))

(def ^:private pages
  {:home #'home/view
   :auth #'auth/view
   :auth/ytm #'auth-ytm/view
   :album #'album/view
   })

(defn main []
  (let [accounts (<sub [:accounts])
        [page args] (<sub [:page])
        page-form [:f> (get pages page) args]]
    (if (or (seq accounts)
            (= "auth" (namespace page)))
      page-form

      [:f> auth/view])))
