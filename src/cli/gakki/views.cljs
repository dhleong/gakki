(ns gakki.views
  (:require [archetype.util :refer [<sub]]
            [gakki.theme :as theme]
            ["ink" :as k]
            [gakki.views.auth :as auth]
            [gakki.views.auth.ytm :as auth-ytm]
            [gakki.views.album :as album]
            [gakki.views.artist :as artist]
            [gakki.views.home :as home]))

(def ^:private pages
  {:home #'home/view
   :auth #'auth/view
   :auth/ytm #'auth-ytm/view
   :album #'album/view
   :artist #'artist/view
   })

(defn main []
  (let [accounts (<sub [:accounts])
        [page args] (<sub [:page])
        page-fn (get pages page)
        page-form [:f> page-fn args]]
    (cond
      (not page-fn)
      [:> k/Text
       [:> k/Text {:background-color "red"} " ERROR "]
       " No page registered for: "
       [:> k/Text {:color theme/header-color-on-background}
        (str [page args])]]

      (or (seq accounts)
          (= "auth" (namespace page)))
      page-form

      :else
      [:f> auth/view])))
