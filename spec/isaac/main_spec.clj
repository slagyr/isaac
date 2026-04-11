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
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["test-dispatch" "--agent" "bot"]))
        (should= "bot" (:agent @received))))

    (it "returns exit code from command run-fn"
      (registry/register! {:name   "fail-cmd"
                           :desc   "Fails"
                           :usage  "fail-cmd"
                           :options []
                           :run-fn (fn [_] 42)})
      (should= 42 (sut/run ["fail-cmd"])))

    (it "returns 0 when run-fn returns nil"
      (registry/register! {:name   "nil-cmd"
                           :desc   "Returns nil"
                           :usage  "nil-cmd"
                           :options []
                           :run-fn (fn [_] nil)})
      (should= 0 (sut/run ["nil-cmd"])))

    (it "shows help for a known command via 'help <cmd>'"
      (registry/register! {:name    "documented"
                           :desc    "A documented command"
                           :usage   "documented [options]"
                           :options [["--verbose" "Be loud"]]
                           :run-fn  identity})
      (should= 0 (sut/run ["help" "documented"])))

    (it "returns 1 for 'help <unknown>'"
      (should= 1 (sut/run ["help" "no-such-command-xyz"])))

    (it "shows help when --help flag is passed to a command"
      (registry/register! {:name    "help-flag-test"
                           :desc    "Has help"
                           :usage   "help-flag-test"
                           :options []
                           :run-fn  (fn [_] (throw (ex-info "should not run" {})))})
      (should= 0 (sut/run ["help-flag-test" "--help"])))

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
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (should= 0 (sut/run ["models" "auth"]))
        (should-not-be-nil @received)))

    (it "does not resolve non-alias prefixes"
      (should= 1 (sut/run ["models" "something-else"]))))

  (describe "parse-opts (via dispatch)"

    (it "parses --agent flag"
      (let [received (atom nil)]
        (registry/register! {:name   "opt-test"
                             :desc   "Test"
                             :usage  "opt-test"
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["opt-test" "--agent" "mybot" "--model" "gpt4"])
        (should= "mybot" (:agent @received))
        (should= "gpt4" (:model @received))))

    (it "parses --resume flag"
      (let [received (atom nil)]
        (registry/register! {:name   "resume-test"
                             :desc   "Test"
                             :usage  "resume-test"
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["resume-test" "--resume"])
        (should= true (:resume @received))))

    (it "parses --session flag"
      (let [received (atom nil)]
        (registry/register! {:name   "session-test"
                             :desc   "Test"
                             :usage  "session-test"
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["session-test" "--session" "key123"])
        (should= "key123" (:session @received))))

    (it "includes _raw-args"
      (let [received (atom nil)]
        (registry/register! {:name   "raw-test"
                             :desc   "Test"
                             :usage  "raw-test"
                             :options []
                             :run-fn (fn [opts] (reset! received opts) 0)})
        (sut/run ["raw-test" "--agent" "x" "extra"])
        (should= ["--agent" "x" "extra"] (:_raw-args @received))))))
