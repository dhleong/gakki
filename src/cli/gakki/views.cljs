(ns gakki.views
  (:require [archetype.util :refer [<sub]]
            [gakki.theme :as theme]
            ["ink" :as k]
            [gakki.cli.dimen :refer [dimens-tracker]]
            [gakki.cli.input :as input]
            [gakki.cli.nav :refer [global-nav]]
            [gakki.views.auth :as auth]
            [gakki.views.auth.ytm :as auth-ytm]
            [gakki.views.album :as album]
            [gakki.views.artist :as artist]
            [gakki.views.home :as home]
            [gakki.views.playlist :as playlist]
            [gakki.views.queue :as queue]
            [gakki.views.search :as search]
            [gakki.views.search-results :as search-results]
            [gakki.views.splash :as splash]))

(def ^:private pages
  {:home #'home/view
   :auth #'auth/view
   :auth/ytm #'auth-ytm/view
   :album #'album/view
   :artist #'artist/view
   :playlist #'playlist/view
   :queue #'queue/view
   :search #'search/view
   :search/results #'search-results/view
   })

(defn main []
  (let [accounts (<sub [:accounts])
        [page args] (<sub [:page])
        page-fn (get pages page)
        page-form [:f> page-fn args]]
    [:<>
     [:f> dimens-tracker]
     [:f> input/dispatcher]

     (when (seq accounts)
       [:f> global-nav])

     (cond
       (nil? accounts)
       [splash/view]

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
       [:f> auth/view])]))
