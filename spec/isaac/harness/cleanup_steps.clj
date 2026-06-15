(ns isaac.harness.cleanup-steps
  "Terminal feature lifecycle hook. Loaded after all other step namespaces
   so after-scenario teardown runs last (stop-server/scheduler first)."
  (:require
    [gherclj.core :as g :refer [helper!]]
    [isaac.foundation.cli-steps :as fcli]))

(helper! isaac.harness.cleanup-steps)

(defn- after-scenario-cleanup! []
  (when-let [f (g/get :isaac-background-future)]
    (future-cancel f)
    (g/dissoc! :isaac-background-future))
  (fcli/teardown-isaac-run-runtime!))

(g/after-scenario after-scenario-cleanup!)