(ns profile-runner
  (:require [clj-async-profiler.core :as prof]
            [speclj.cli :refer [run]]))

(defn -main [& _]
  ;; Run once to warm up the JVM before profiling
  (run "-c" "-D" "spec")
  ;; Profile 10 runs so JIT noise is amortized
  (let [exit-code (prof/profile {}
                    (dotimes [_ 10]
                      (run "-c" "-D" "spec")))]
    (System/exit exit-code)))
