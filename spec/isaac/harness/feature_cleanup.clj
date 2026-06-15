(ns isaac.harness.feature-cleanup
  "Terminal feature lifecycle hook. Loaded explicitly after isaac.**-steps
   glob resolution so server/scheduler shutdown and runtime teardown always
   run last, in a fixed order (filesystem scan order is OS-dependent)."
  (:require
    [gherclj.core :as g :refer [helper!]]
    [isaac.bridge.cancellation :as bridge-cancel]
    [isaac.foundation.cli-steps :as fcli]
    [isaac.llm.api.grover :as grover]
    [isaac.nexus :as nexus]
    [isaac.scheduler.runtime :as scheduler]
    [isaac.server.app :as app]))

(helper! isaac.harness.feature-cleanup)

(defn- cancel-turn-future! [k]
  (when-let [f (g/get k)]
    (future-cancel f)
    (g/dissoc! k)))

(defn- after-scenario-cleanup! []
  (cancel-turn-future! :turn-future)
  (cancel-turn-future! :acp-turn-future)
  (when-let [f (g/get :isaac-background-future)]
    (future-cancel f)
    (g/dissoc! :isaac-background-future))
  (bridge-cancel/clear!)
  (grover/reset-queue!)
  (when-let [sched (or (g/get :scheduler) (nexus/get :scheduler))]
    (scheduler/stop! sched))
  (app/stop!)
  (fcli/teardown-isaac-run-runtime!))

(g/after-scenario after-scenario-cleanup!)