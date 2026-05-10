(ns profile-runner
  (:require [clj-async-profiler.core :as prof]
            [speclj.cli :refer [run]]))

(defn -main [& _]
  (let [exit-code (prof/profile {}
                    (run "-c" "-D" "spec"))]
    (System/exit exit-code)))
