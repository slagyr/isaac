(ns isaac.cli.registry-spec
  (:require
    [isaac.cli.registry :as sut]
    [speclj.core :refer :all]))

(describe "CLI Registry"

  (around [it]
    (let [saved @(deref #'sut/commands)]
      (reset! @#'sut/commands {})
      (it)
      (reset! @#'sut/commands saved)))

  (describe "register!"

    (it "registers a command by name"
      (sut/register! {:name "test-cmd" :desc "A test" :run-fn identity})
      (should-not-be-nil (sut/get-command "test-cmd")))

    (it "stores all command fields"
      (let [cmd {:name    "greet"
                 :usage   "greet [name]"
                 :desc    "Say hello"
                 :options [["--loud" "Shout it"]]
                 :run-fn  identity}]
        (sut/register! cmd)
        (let [stored (sut/get-command "greet")]
          (should= "greet" (:name stored))
          (should= "greet [name]" (:usage stored))
          (should= "Say hello" (:desc stored))
          (should= [["--loud" "Shout it"]] (:options stored)))))

    (it "overwrites a command with the same name"
      (sut/register! {:name "dup" :desc "First" :run-fn identity})
      (sut/register! {:name "dup" :desc "Second" :run-fn identity})
      (should= "Second" (:desc (sut/get-command "dup")))))

  (describe "get-command"

    (it "returns nil for unknown command"
      (should-be-nil (sut/get-command "nonexistent")))

    (it "returns the registered command"
      (sut/register! {:name "found" :desc "Here" :run-fn identity})
      (should= "Here" (:desc (sut/get-command "found")))))

  (describe "all-commands"

    (it "returns empty list when nothing registered"
      (should= [] (sut/all-commands)))

    (it "returns all commands sorted by name"
      (sut/register! {:name "beta" :desc "B" :run-fn identity})
      (sut/register! {:name "alpha" :desc "A" :run-fn identity})
      (sut/register! {:name "gamma" :desc "G" :run-fn identity})
      (let [names (map :name (sut/all-commands))]
        (should= ["alpha" "beta" "gamma"] names))))

  (describe "command-help"

    (it "formats help text with usage, desc, and options"
      (let [cmd {:name    "chat"
                 :usage   "chat [options]"
                 :desc    "Start a chat"
                 :options [["--model <m>" "Model to use"]
                           ["--resume"    "Resume session"]]}
            help (sut/command-help cmd)]
        (should-contain "Usage: isaac chat [options]" help)
        (should-contain "Start a chat" help)
        (should-contain "Options:" help)
        (should-contain "--model <m>" help)
        (should-contain "--resume" help)))

    (it "renders help without options"
      (let [cmd  {:name "info" :usage "info" :desc "Show info" :options []}
            help (sut/command-help cmd)]
        (should-contain "Usage: isaac info" help)
        (should-contain "Show info" help)
        (should-contain "Options:" help)))))
