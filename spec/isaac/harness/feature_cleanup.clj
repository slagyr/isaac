(ns isaac.harness.feature-cleanup
  "Terminal feature lifecycle hook. Loaded explicitly after isaac.**-steps
   glob resolution so runtime teardown always runs last."
  (:require
    [gherclj.core :as g :refer [helper!]]
    [isaac.foundation.cli-steps :as fcli]))

(helper! isaac.harness.feature-cleanup)

(defn- after-scenario-cleanup! []
  (when-let [f (g/get :isaac-background-future)]
    (future-cancel f)
    (g/dissoc! :isaac-background-future))
  (fcli/teardown-isaac-run-runtime!))

(g/after-scenario after-scenario-cleanup!)