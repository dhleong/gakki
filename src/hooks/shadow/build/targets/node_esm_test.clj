(ns shadow.build.targets.node-esm-test
  (:require
   [shadow.build :as build]
   [shadow.build.api :as build-api]
   [shadow.build.targets.esm :as esm]
   [shadow.build.targets.node-test :as node-test]))

(defn configure [{::build/keys [config] :as state}]
  (-> state
      (node-test/configure)
      (update ::build/config dissoc :main)

      (assoc-in [::build/config :devtools :enabled] false)

      (cond->
       (not (get-in config [:js-options :js-provider]))
       ; NOTE: It's not immediately clear to me why we need both, but 
       ; if we omit the second we get complaints about:
       ;   JS dependency "process" is not available
       ; and if we omit the first we get complaints about "require is not
       ; defined in ES module scope"
        (-> (build-api/with-js-options {:js-provider :import})
            (update ::build/config build-api/with-js-options {:js-provider :import})))

      (update-in [::build/config :modules] merge {:main (get-in config [::build/config :modules] {:init-fn 'shadow.test.node/main})})))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defn process
  [{::build/keys [stage config] :as state}]
  (when (:autorun config)
    (throw (ex-info "autorun not yet supported for :node-esm-test"
                    {:tag ::autorun-support})))
  (-> state
      (cond->
       (= stage :configure)
        (configure)

        (= stage :resolve)
        (node-test/test-resolve))

      (esm/process)))
