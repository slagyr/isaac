(ns isaac.features.steps.cli
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defwhen defthen]]
    [isaac.main :as main]))

(defwhen isaac-run "isaac is run with {args:string}"
  [args]
  (let [argv      (if (str/blank? args)
                    []
                    (str/split args #"\s+"))
        output    (with-out-str
                    (let [code (main/run argv)]
                      (g/assoc! :exit-code code)))]
    (g/assoc! :output output)))

(defthen output-contains "the output contains {expected:string}"
  [expected]
  (let [output (g/get :output)]
    (g/should (str/includes? output expected))))

(defthen exit-code-is "the exit code is {int}"
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (g/get :exit-code))))
