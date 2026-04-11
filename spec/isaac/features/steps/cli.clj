(ns isaac.features.steps.cli
  (:require
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.main :as main]
    [isaac.util.shell :as shell]))

(defwhen isaac-run "isaac is run with {args:string}"
  [args]
  (let [argv           (if (str/blank? args)
                         []
                         (str/split args #"\s+"))
        api-key-login? (and (= "auth" (first argv))
                            (= "login" (second argv))
                            (some #(= "--api-key" %) argv))
        cmd-stub       (g/get :cmd-stub)
        run!           (fn []
                         (let [code (main/run argv)]
                           (g/assoc! :exit-code code)))
        run-with-stubs (fn []
                         (if api-key-login?
                           (with-redefs [read-line (fn [] "sk-test-key")]
                             (run!))
                           (run!)))
        output         (with-out-str
                         (if cmd-stub
                           (with-redefs [shell/cmd-available? (fn [cmd] (get cmd-stub cmd false))]
                             (run-with-stubs))
                           (run-with-stubs)))]
    (g/assoc! :output output)))

(defthen output-contains "the output contains {expected:string}"
  [expected]
  (let [output (g/get :output)]
    (g/should (str/includes? output expected))))

(defthen exit-code-is "the exit code is {int}"
  [code]
  (let [code (if (string? code) (parse-long code) code)]
    (g/should= code (g/get :exit-code))))

(defgiven command-available "the command {cmd:string} is available"
  [cmd]
  (g/assoc! :cmd-stub {cmd true}))

(defgiven command-not-available "the command {cmd:string} is not available"
  [cmd]
  (g/assoc! :cmd-stub {cmd false}))
