(ns isaac.cli.init-spec
  (:require
    [clojure.edn :as edn]
    [isaac.cli.init :as sut]
    [isaac.main :as main]
    [isaac.cli.registry :as registry]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io StringWriter)))

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
    (should-not-be-nil (registry/get-command "init")))

  (it "scaffolds the default config files in a fresh home"
    (should= 0 (sut/run {:home test-home}))
    (should= {:defaults {:crew :main :model :llama}
               :tz "America/Chicago"
               :prefer-entity-files? true
               :cron {:heartbeat {:expr "*/30 * * * *"
                                  :crew :main}}}
              (slurp-edn (str test-home "/.isaac/config/isaac.edn")))
    (should= {:model :llama}
              (slurp-edn (str test-home "/.isaac/config/crew/main.edn")))
    (should= "You are Isaac, a helpful AI assistant."
             (fs/slurp (str test-home "/.isaac/config/crew/main.md")))
    (should= {:model "llama3.2" :provider :ollama}
             (slurp-edn (str test-home "/.isaac/config/models/llama.edn")))
    (should= {:base-url "http://localhost:11434" :api :ollama}
             (slurp-edn (str test-home "/.isaac/config/providers/ollama.edn")))
    (should= {:expr "*/30 * * * *" :crew :main}
             (slurp-edn (str test-home "/.isaac/config/cron/heartbeat.edn")))
    (should= "Heartbeat. Anything worth noting?"
             (fs/slurp (str test-home "/.isaac/config/cron/heartbeat.md"))))

  (it "prints the scaffold summary and ollama setup instructions on success"
    (should= 0 (sut/run {:home test-home}))
    (should= (str "Isaac initialized at " test-home ".\n\n"
                  "Created:\n"
                  "  config/isaac.edn\n"
                  "  config/crew/main.edn\n"
                  "  config/crew/main.md\n"
                  "  config/models/llama.edn\n"
                  "  config/providers/ollama.edn\n"
                  "  config/cron/heartbeat.edn\n"
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
    (should= 1 (sut/run {:home test-home}))
    (should= (str "config already exists at " test-home "/.isaac/config/isaac.edn; edit it directly.\n")
             (str *err*)))

  (it "appears in top-level help output"
    (let [output (with-out-str (should= 0 (main/run ["--help"])))]
      (should-contain "init" output)))

  (it "scaffolds config under a resolved home directory"
    (should= 0 (main/run ["--home" test-home "init"]))
    (should (fs/exists? (str test-home "/.isaac/config/isaac.edn")))))
