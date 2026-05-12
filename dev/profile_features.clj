(ns profile-features
  (:require [clj-async-profiler.core :as prof]
            [clojure.java.io :as io]
            [gherclj.main]))

(defn- clean-generated! []
  (let [dir (io/file "target/gherclj/generated")]
    (when (.exists dir)
      (doseq [file (reverse (file-seq dir))]
        (.delete file)))))

(defn- run-features []
  (clean-generated!)
  (gherclj.main/run ["-f" "features"
                     "-f" "modules/*/features"
                     "-s" "isaac.features.steps.*"
                     "-t" "~slow"
                     "-t" "~wip"]))

(defn -main [& _]
  ;; Warmup run — loads all step namespaces and warms the JIT
  (run-features)
  ;; Profile 2 runs for representative data without excessive wall time
  (let [exit-code (prof/profile {}
                    (run-features)
                    (run-features))]
    (System/exit exit-code)))
