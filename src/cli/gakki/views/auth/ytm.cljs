(ns gakki.views.auth.ytm
  (:require ["clipboardy" :default clipboard]
            ["ink" :as k]
            ["ink-spinner" :default Spinner]
            ["open" :default open]
            [archetype.util :refer [<sub >evt]]
            [clojure.core.match :as m]
            [gakki.accounts :as accounts]
            [gakki.accounts.core :as ap]
            [gakki.accounts.ytm.creds :refer [get-innertube-user-info
                                              login-with-innertube]]
            [gakki.cli.input :refer [use-input]]
            [gakki.theme :as theme]
            [gakki.util.logging :as log]
            [promesa.core :as p]
            [reagent.core :as r]))

(defn- logged-in [account]
  (let [on-delete #(>evt [:auth/delete :ytm])]
    (use-input
     {:delete on-delete
      :backspace on-delete}))

  [:<>
   [:> k/Text {:color theme/header-color-on-background}
    "YouTube Music: " (ap/describe-account
                       (:ytm accounts/providers)
                       account)]

   [:> k/Text " "]

   [:> k/Text {:color theme/text-color-disabled}
    "Press <delete> or <backspace> to logout"]])

; (defn- perform-login [state]
;   (-> (p/let [auth-code (request-auth-code)
;               _ (reset! state :exchanging)

;               js-info (exchange-auth-code auth-code)
;               auth (js->clj js-info :keywordize-keys true)
;               user (fetch-user-info auth)
;               account (assoc auth :user user)]
;         (>evt [:auth/save :ytm account]))

;       (p/catch (fn [e]
;                  (log/error "Failed to login to YTM:" e)
;                  (reset! state :error)))))

(defn- get-creds [state]
  (login-with-innertube
   {:on-url (fn [{:keys [url code]}]
              ; TODO: Probably, refactor out to shared util?
              (open url)
              ((.-write clipboard) code)
              (reset! state [:started
                             {:url url
                              :code code}])

              (println url)
              (println code))}))

(defn- perform-login [state]
  (-> (p/let [auth (get-creds state)
              _ (reset! state :exchanging)
              user (get-innertube-user-info auth)
              account (assoc auth :user user)]
        (println "GOT: " account)
        (>evt [:auth/save :ytm account]))
      (p/catch (fn [e]
                 (log/error "Failed to login to YTM:" e)
                 (reset! state :error)))))

(defn- logged-out []
  (r/with-let [state (r/atom nil)]
    (use-input
     {:return (fn []
                (when (let [s @state]
                        (or (nil? s)
                            (= :error s)))
                  (reset! state :started)
                  (perform-login state)))})

    (m/match [@state]
      [nil] [:<>
             [:> k/Text {:color theme/text-color-on-background}
              "A browser window will open for you to login to the Google Account "
              "you wish to use with YouTube Music."]
             [:> k/Text " "]
             [:> k/Text {:color theme/text-color-on-background}
              "Press"
              [:> k/Text {:color theme/accent-color} " ENTER "]
              "to continue"]]

      [[:started {:url url :code code}]]
      [:<>
       [:> k/Text {:color theme/text-color-disabled}
        "Proceed with signin using the opened browser window:"]
       [:> k/Text {:color :theme/text-color-on-background}
        "  " url]
       [:> k/Text {:color theme/text-color-disabled}
        "When prompted, enter code:"]
       [:> k/Text {:color :theme/text-color-on-background}
        "  " code]
       [:> k/Text {:color theme/text-color-disabled}
        "(copied to clipboard)"]]

      [:started] [:<>
                  [:> k/Text {:color theme/text-color-disabled}
                   "Proceed with signin using the opened browser window"]]

      [:exchanging] [:<>
                     [:> k/Box {:flex-direction :row}
                      [:> Spinner {:type "dots"}]
                      [:> k/Text {:color theme/text-color-on-background}
                       "Completing login process..."]]]

      [:error] [:<>
                [:> k/Text {:color theme/header-color-on-background}
                 "Something went wrong."]
                [:> k/Text " "]
                [:> k/Text {:color theme/text-color-on-background}
                 "Press"
                 [:> k/Text {:color theme/accent-color} " ENTER "]
                 "to try again."]])))

(defn view []
  (use-input
   {:escape #(>evt [:navigate/replace! [:auth]])})

  (let [account (<sub [:account :ytm])]
    [:> k/Box {:flex-direction :column
               :border-color theme/text-color-on-background
               :border-style :round
               :padding-x 1}
     (if account
       [:f> logged-in account]
       [:f> logged-out])

     [:<>
      [:> k/Text " "]
      [:> k/Text {:color theme/text-color-disabled}
       "Press "
       [:> k/Text {:color theme/text-color-on-background} "<esc>"]
       " to return"]]]))
