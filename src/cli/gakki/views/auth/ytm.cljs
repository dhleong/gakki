(ns gakki.views.auth.ytm
  (:require
   ["ink" :as k]
   ["ink-spinner" :default Spinner]
   [archetype.util :refer [<sub >evt]]
   [clojure.core.match :as m]
   [gakki.accounts :as accounts]
   [gakki.accounts.core :as ap]
   [gakki.accounts.ytm.cookies :refer [request-cookies]]
   [gakki.accounts.ytm.creds :refer [get-innertube-user-info]]
   [gakki.cli.input :refer [use-input]]
   [gakki.theme :as theme]
   [gakki.util.logging :as log]
   [promesa.core :as p]
   [reagent.core :as r]))

(defn- managed-reset! [state new-state]
  (swap! state
         (fn [old]
           (m/match [old]
             [[:opened {:promise p}]]
             (p/cancel p)

             :else nil)
           new-state)))

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

(defn- perform-login [state]
  (let [p (request-cookies)]
    (managed-reset! state [:opened {:promise p}])
    (-> (p/let [auth p
                _ (println "GOT " auth)
                _ (reset! state :exchanging)
                user (get-innertube-user-info auth)
                account (assoc auth :user user)]
          (println "GOT: " account)
          (>evt [:auth/save :ytm account]))
        (p/catch (fn [e]
                   (log/error "Failed to login to YTM:" e)
                   (managed-reset! state :error))))))

(defn- logged-out []
  (r/with-let [state (r/atom nil)]
    (use-input
     {:return (fn []
                (when (let [s @state]
                        (or (nil? s)
                            (= :error s)))
                  (managed-reset! state :started)
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

      [(:or :started
            [:opened _])]
      [:<>
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
                 "to try again."]])

    (finally
      (println "Unmounted logged-out: " @state)
      (managed-reset! state nil))))

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
