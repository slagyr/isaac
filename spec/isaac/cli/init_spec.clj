(ns isaac.cli.init-spec
  (:require
    [clojure.edn :as edn]
    [isaac.cli.init :as sut]
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
    (should= {:defaults {:crew :main :model :default}
              :tz "America/Chicago"
              :prefer-entity-files? true
              :cron {:heartbeat {:expr "0 0 * * *"
                                 :crew :main
                                 :prompt "Daily heartbeat. Anything worth noting?"}}}
             (slurp-edn (str test-home "/.isaac/config/isaac.edn")))
    (should= {:model :default}
             (slurp-edn (str test-home "/.isaac/config/crew/main.edn")))
    (should= "You are Isaac, a helpful AI assistant."
             (fs/slurp (str test-home "/.isaac/config/crew/main.md")))
    (should= {:model "llama3.2" :provider :ollama}
             (slurp-edn (str test-home "/.isaac/config/models/default.edn")))
    (should= {:base-url "http://localhost:11434" :api "ollama"}
             (slurp-edn (str test-home "/.isaac/config/providers/ollama.edn"))))

  (it "prints ollama setup instructions on success"
    (should= 0 (sut/run {:home test-home}))
    (should-contain "Isaac initialized" (str *out*))
    (should-contain "brew install ollama" (str *out*))
    (should-contain "ollama serve" (str *out*))
    (should-contain "ollama pull llama3.2" (str *out*)))

  (it "refuses when a config already exists"
    (fs/mkdirs (str test-home "/.isaac/config"))
    (fs/spit (str test-home "/.isaac/config/isaac.edn") "{}")
    (should= 1 (sut/run {:home test-home}))
    (should-contain "config already exists" (str *err*))))
