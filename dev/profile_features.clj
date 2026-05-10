(ns profile-features
  (:require [clj-async-profiler.core :as prof]
            [gherclj.main]))

(defn- run-features []
  (gherclj.main/run ["-f" "features"
                     "-f" "modules/*/features"
                     "-s" "isaac.features.steps.*"]))

(defn -main [& _]
  ;; Warmup run — loads all step namespaces and warms the JIT
  (run-features)
  ;; Profile 2 runs for representative data without excessive wall time
  (let [exit-code (prof/profile {}
                    (run-features)
                    (run-features))]
    (System/exit exit-code)))
