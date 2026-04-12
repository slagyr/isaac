(ns isaac.main-spec
  (:require
    [isaac.cli.registry :as registry]
    [isaac.main :as sut]
    [speclj.core :refer :all]))

(describe "Main CLI"

  (around [it]
    (binding [*out* (java.io.StringWriter.)]
      (it)))

  (describe "run"

    (it "prints usage and returns 0 when no args"
      (should= 0 (sut/run [])))

    (it "prints usage and returns 0 for blank command"
      (should= 0 (sut/run [""])))

    (it "prints usage and returns 0 for help command"
      (should= 0 (sut/run ["help"])))

    (it "returns 1 for unknown command"
      (should= 1 (sut/run ["nonexistent-command-xyz"])))

    (it "dispatches to a registered command"
      (let [received (atom nil)]
        (registry/register! {:name   "test-dispatch"
                             :desc   "Test"
                             :usage  "test-dispatch"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["test-dispatch" "--agent" "bot"]))
        (should= ["--agent" "bot"] (:_raw-args @received))))

    (it "returns exit code from command run-fn"
      (registry/register! {:name   "fail-cmd"
                           :desc   "Fails"
                           :usage  "fail-cmd"
                           :option-spec []
                           :run-fn (fn [_] 42)})
      (should= 42 (sut/run ["fail-cmd"])))

    (it "returns 0 when run-fn returns nil"
      (registry/register! {:name   "nil-cmd"
                           :desc   "Returns nil"
                           :usage  "nil-cmd"
                           :option-spec []
                           :run-fn (fn [_] nil)})
      (should= 0 (sut/run ["nil-cmd"])))

    (it "shows help for a known command via 'help <cmd>'"
      (registry/register! {:name    "documented"
                           :desc    "A documented command"
                           :usage   "documented [options]"
                           :option-spec [["-v" "--verbose" "Be loud"]]
                           :run-fn  identity})
      (should= 0 (sut/run ["help" "documented"])))

    (it "returns 1 for 'help <unknown>'"
      (should= 1 (sut/run ["help" "no-such-command-xyz"])))

    (it "shows help when --help flag is passed to a command"
      (let [received (atom nil)]
        (registry/register! {:name        "help-flag-test"
                             :desc        "Has help"
                             :usage       "help-flag-test"
                             :option-spec []
                             :run-fn      (fn [opts]
                                            (reset! received opts)
                                            0)})
        (should= 0 (sut/run ["help-flag-test" "--help"]))
        (should= ["--help"] (:_raw-args @received))))

    (it "prints usage and returns 0 for top-level --help"
      (should= 0 (sut/run ["--help"])))

    (it "prints usage and returns 0 for top-level -h"
      (should= 0 (sut/run ["-h"]))))

  (describe "alias resolution"

    (it "resolves 'models auth' to 'auth'"
      (let [received (atom nil)]
        (registry/register! {:name   "auth"
                             :desc   "Auth"
                             :usage  "auth"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["models" "auth"]))
        (should-not-be-nil @received)))

    (it "does not resolve non-alias prefixes"
      (should= 1 (sut/run ["models" "something-else"]))))

  (describe "dispatch payload"

    (it "includes _raw-args"
      (let [received (atom nil)]
        (registry/register! {:name   "raw-test"
                             :desc   "Test"
                             :usage  "raw-test"
                             :option-spec []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["raw-test" "--agent" "x" "extra"])
        (should= ["--agent" "x" "extra"] (:_raw-args @received))))

    (it "includes bound extra opts"
      (let [received (atom nil)]
        (registry/register! {:name        "extra-test"
                             :desc        "Test"
                             :usage       "extra-test"
                             :option-spec []
                             :run-fn      (fn [opts] (reset! received opts) 0)})
        (binding [sut/*extra-opts* {:state-dir "target/test-state"}]
          (sut/run ["extra-test"]))
        (should= "target/test-state" (:state-dir @received))))))
