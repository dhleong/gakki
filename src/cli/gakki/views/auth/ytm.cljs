(ns gakki.views.auth.ytm
  (:require [archetype.util :refer [<sub >evt]]
            [applied-science.js-interop :as j]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            [promesa.core :as p]
            [reagent.core :as r]
            ["youtubish/dist/auth" :rename {exchangeAuthCode exchange-auth-code}]
            ["youtubish/dist/login" :rename {requestAuthCode request-auth-code}]
            [gakki.accounts :as accounts]
            [gakki.accounts.core :as ap]
            [gakki.theme :as theme]))

(defn- logged-in [account]
  [:<>
   [:> k/Text {:color theme/header-color-on-background}
    "YouTube Music: " (ap/describe-account
                        (:ytm accounts/providers)
                        account)]])

(defn- perform-login [state]
  (-> (p/let [auth-code (request-auth-code)
              _ (reset! state :exchanging)
              info (exchange-auth-code auth-code)]
        ; TODO
        (println info)
        (reset! state nil))

      (p/catch (fn [e]
                 (println e)
                 (reset! state :error)))))

(defn- logged-out []
  (r/with-let [state (r/atom nil)]
    (k/useInput
      (fn [_input k]
        (when (and (j/get k :return)
                   (let [s @state]
                     (or (nil? s)
                         (= :error s))))
          (reset! state :started)
          (perform-login state))))

    (case @state
      nil [:<>
           [:> k/Text {:color theme/text-color-on-background}
            "A browse window will open for you to login to the Google Account "
            "you wish to use with YouTube Music."]
           [:> k/Text " "]
           [:> k/Text {:color theme/text-color-on-background}
            "Press"
            [:> k/Text {:color theme/accent-color} " ENTER "]
            "to continue"]]

      :started [:<>
                [:> k/Text {:color theme/text-color-disabled}
                 "Proceed with signin using the opened browser window"]]

      :exchanging [:<>
                   [:> k/Box {:flex-direction :row}
                    [:> Spinner {:type "dots"}]
                    [:> k/Text {:color theme/text-color-on-background}
                     "Completing login process..."]]]

      :error [:<>
              [:> k/Text {:color theme/header-color-on-background}
               "Something went wrong."]
              [:> k/Text " "]
              [:> k/Text {:color theme/text-color-on-background}
               "Press"
               [:> k/Text {:color theme/accent-color} " ENTER "]
               "to try again."]]
      )))

(defn view []
  (k/useInput
    (fn [_input k]
      (when (j/get k :escape)
        (>evt [:navigate! [:auth]]))))

  (let [account (<sub [:account :ytm])]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}

     (if account
       [logged-in account]
       [:f> logged-out])

     [:<>
      [:> k/Text " "]
      [:> k/Text {:color theme/text-color-disabled}
       "Press "
       [:> k/Text {:color theme/text-color-on-background} "<esc>"]
       " to return"]]
     ]))
