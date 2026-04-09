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
        api-key-login? (and (= "auth" (first argv))
                            (= "login" (second argv))
                            (some #(= "--api-key" %) argv))
        output    (with-out-str
                    (let [run! (fn []
                                 (let [code (main/run argv)]
                                   (g/assoc! :exit-code code)))]
                      (if api-key-login?
                        (with-redefs [read-line (fn [] "sk-test-key")]
                          (run!))
                        (run!))))]
    (g/assoc! :output output)))

(defthen output-contains "the output contains {expected:string}"
  [expected]
  (let [output (g/get :output)]
    (g/should (str/includes? output expected))))

(defthen exit-code-is "the exit code is {int}"
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (g/get :exit-code))))
