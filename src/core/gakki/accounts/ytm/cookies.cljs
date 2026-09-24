(ns gakki.accounts.ytm.cookies
  (:require
   [applied-science.js-interop :as j]
   [clojure.string :as str]
   [gakki.util.paths :as paths]
   [patchright :refer [chromium]]
   [promesa.core :as p]))

(def ^:private logged-in-url "https://music.youtube.com/")
(def ^:private cookie-urls
  #js ["https://youtube.com"
       logged-in-url
       #_"https://google.com"])

(defonce ^:private last-session (atom nil))

(defn- get-ytm-cookies [browser-or-page]
  (p/let [^js context (or (when (.-context browser-or-page)
                            (.context browser-or-page))
                          browser-or-page)
          cookies (.cookies context cookie-urls)]
    (def last-cookies cookies)
    (->> cookies
         (map (j/fn [^:js {:keys [name value]}]
                (str name "=" value)))
         (str/join "; "))))

(defn request-cookies []
  (let [resolve! (volatile! nil)
        reject! (volatile! nil)
        deferred (p/create
                  (fn [res rej]
                    (vreset! resolve! res)
                    (vreset! reject! rej)))
        abort-controller (js/AbortController.)]
    (-> deferred
        (p/catch
         (fn [_]
           (when (p/cancelled? deferred)
             (.abort abort-controller)))))
    (->
     (p/let [^js browser (-> chromium
                             (.launchPersistentContext
                              (paths/internal-data "ytm.auth")
                              #js {:channel "chrome"
                                   :headless false}))
             [existing ^js new-page] (p/all
                                      [(.pages browser)
                                       (.newPage browser)])]
       ; Prep graceful cleanup
       ; (the vec wrapper avoids promise deref)
       [(-> deferred
            (p/finally
              (fn []
                (.close browser))))]

       ; Slightly cleaner UX
       (when (and (= 1 (count existing))
                  (= (.url (first existing))
                     "about:blank"))
         (.close (first existing)))

       (reset! last-session new-page)

       (println "goto...")
       (.goto new-page "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F")
       (println "<< goto...")

       (when-not (= (.url new-page) logged-in-url)
         (println "AT " (.url new-page))
         (.waitForUrl
          new-page
          logged-in-url
          #js {:timeout 0
               :abortSignal (j/get abort-controller .-signal)}))
       (println "<< waited...")

       (p/let [cookies-str (get-ytm-cookies new-page)]
         {:cookie cookies-str}))
     (p/catch #(@reject! %))
     (p/then #(@resolve! %)))
    deferred))
