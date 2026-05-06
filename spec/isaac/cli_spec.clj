(ns isaac.cli-spec
  (:require
    [clojure.edn :as edn]
    [isaac.cli :as sut]
    [isaac.main :as main]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io StringWriter)))

;; region ----- Registry -----

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
                 :option-spec [["-l" "--loud" "Shout it"]]
                 :run-fn  identity}]
        (sut/register! cmd)
        (let [stored (sut/get-command "greet")]
          (should= "greet" (:name stored))
          (should= "greet [name]" (:usage stored))
          (should= "Say hello" (:desc stored))
          (should= [["-l" "--loud" "Shout it"]] (:option-spec stored)))))

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
                 :option-spec [["-m" "--model MODEL" "Model to use"]
                               ["-r" "--resume" "Resume session"]]}
            help (sut/command-help cmd)]
        (should-contain "Usage: isaac chat [options]" help)
        (should-contain "Start a chat" help)
        (should-contain "Options:" help)
        (should-contain "--model MODEL" help)
        (should-contain "--resume" help)))

    (it "renders help without options"
      (let [cmd  {:name "info" :usage "info" :desc "Show info" :option-spec []}
            help (sut/command-help cmd)]
        (should-contain "Usage: isaac info" help)
        (should-contain "Show info" help)
        (should-contain "Options:" help)))))

;; endregion ^^^^^ Registry ^^^^^

;; region ----- Init -----

(def test-home "/test/init")

(defn- slurp-edn [path]
  (edn/read-string (fs/slurp path)))

(describe "CLI Init"

  (around [it]
    (binding [*out* (StringWriter.)
              *err* (StringWriter.)
              fs/*fs* (fs/mem-fs)]
      (it)))

  (it "registers the init command"
    (should-not-be-nil (sut/get-command "init")))

  (it "scaffolds the default config files in a fresh home"
    (should= 0 (sut/init-run {:home test-home}))
    (should= {:defaults {:crew :main :model :llama}
               :tz "America/Chicago"
               :prefer-entity-files true}
              (slurp-edn (str test-home "/.isaac/config/isaac.edn")))
    (should= (str "---\n"
                  "{:model :llama}\n"
                  "---\n\n"
                  "You are Isaac, a helpful AI assistant.")
             (fs/slurp (str test-home "/.isaac/config/crew/main.md")))
    (should= {:model "llama3.2" :provider :ollama}
             (slurp-edn (str test-home "/.isaac/config/models/llama.edn")))
    (should= {:base-url "http://localhost:11434" :api :ollama}
              (slurp-edn (str test-home "/.isaac/config/providers/ollama.edn")))
    (should= (str "---\n"
                  "{:expr \"*/30 * * * *\", :crew :main}\n"
                  "---\n\n"
                  "Heartbeat. Anything worth noting?")
             (fs/slurp (str test-home "/.isaac/config/cron/heartbeat.md"))))

  (it "prints the scaffold summary and ollama setup instructions on success"
    (should= 0 (sut/init-run {:home test-home}))
    (should= (str "Isaac initialized at " test-home ".\n\n"
                  "Created:\n"
                  "  config/isaac.edn\n"
                  "  config/crew/main.md\n"
                  "  config/models/llama.edn\n"
                  "  config/providers/ollama.edn\n"
                  "  config/cron/heartbeat.md\n\n"
                  "Isaac uses Ollama locally. If you don't have it:\n\n"
                  "  brew install ollama\n"
                  "  ollama serve &\n"
                  "  ollama pull llama3.2\n\n"
                  "Then try:\n\n"
                  "  isaac prompt -m \"hello\"\n")
             (str *out*)))

  (it "refuses when a config already exists"
    (fs/mkdirs (str test-home "/.isaac/config"))
    (fs/spit (str test-home "/.isaac/config/isaac.edn") "{}")
    (should= 1 (sut/init-run {:home test-home}))
    (should= (str "config already exists at " test-home "/.isaac/config/isaac.edn; edit it directly.\n")
             (str *err*)))

  (it "appears in top-level help output"
    (let [output (with-out-str (should= 0 (main/run ["--help"])))]
      (should-contain "init" output)))

  (it "scaffolds config under a resolved home directory"
    (should= 0 (main/run ["--home" test-home "init"]))
    (should (fs/exists? (str test-home "/.isaac/config/isaac.edn")))))

;; endregion ^^^^^ Init ^^^^^
