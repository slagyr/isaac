(ns isaac.config.cli.command-spec
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.cli.command :as sut]
    [isaac.cli.registry :as registry]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io BufferedReader StringReader StringWriter)))

(def test-home "/test/config-cli")

(defn- write-config! [path data]
  (fs/mkdirs (fs/parent path))
  (fs/spit path (pr-str data)))

(describe "CLI Config"

  (around [it]
    (binding [*out* (StringWriter.)
              *err* (StringWriter.)
              *in*  (BufferedReader. (StringReader. ""))
              fs/*fs* (fs/mem-fs)]
      (reset! c3env/-overrides {})
      (it)))

  (describe "run"

    (it "prints help and returns 0 with --help"
      (should= 0 (sut/run {:home test-home} ["--help"])))

    (it "fails clearly when no config exists"
      (should= 1 (sut/run {:home test-home} []))
      (should-contain "no config found" (str *err*))
      (should-contain "/test/config-cli/.isaac/config/isaac.edn" (str *err*)))

    (it "prints resolved config with env values redacted by default"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} []))
      (should-contain "<CONFIG_TEST_API_KEY:redacted>" (str *out*))
      (should-not-contain "sk-test-123" (str *out*)))

    (it "prints resolved config across multiple lines"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                      {:defaults {:crew :main :model :llama}
                       :crew {:main {}
                              :marvin {:model :llama :soul "You are Marvin."}}
                       :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} []))
      (should (< 5 (count (str/split-lines (str *out*))))))

    (it "prints raw config without substitution"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} ["--raw"]))
      (should-contain "${CONFIG_TEST_API_KEY}" (str *out*))
      (should-not-contain "redacted" (str *out*)))

    (it "prints raw config across multiple lines"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                      {:defaults {:crew :main :model :llama}
                       :crew {:main {}
                              :marvin {:model :llama :soul "You are Marvin."}}
                       :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (should= 0 (sut/run {:home test-home} ["--raw"]))
      (should (< 5 (count (str/split-lines (str *out*))))))

    (it "reveals actual values only after typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["--reveal"])))
      (should-contain "type REVEAL to confirm:" (str *err*))
      (should-contain "sk-test-123" (str *out*)))

    (it "prints revealed config across multiple lines"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                      {:defaults {:crew :main :model :llama}
                       :crew {:main {}
                              :marvin {:model :llama :soul "You are Marvin."}}
                       :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["--reveal"])))
      (should (< 5 (count (str/split-lines (str *out*))))))

    (it "refuses reveal without typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 1 (sut/run {:home test-home} ["--reveal"]))
      (should-contain "type REVEAL to confirm:" (str *err*))
      (should-contain "Refusing to reveal config." (str *err*))
      (should-not-contain "sk-test-123" (str *out*)))

    (it "refuses reveal after a non-matching confirmation line"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "blah\n"))]
        (should= 1 (sut/run {:home test-home} ["--reveal"])))
      (should= 1 (count (re-seq #"type REVEAL to confirm:" (str *err*))))
      (should-contain "Refusing to reveal config." (str *err*))
      (should-not-contain "sk-test-123" (str *out*)))

  )

  (describe "validate"

    (it "fails clearly when no config exists"
      (should= 1 (sut/run {:home test-home} ["validate"]))
      (should-contain "no config found" (str *err*)))

    (it "prints OK and returns 0 when validation passes"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                      {:defaults {:crew :main :model :llama}
                       :crew {:main {:soul "You are Isaac."}}
                       :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                       :providers {:anthropic {}}})
      (should= 0 (sut/run {:home test-home} ["validate"]))
      (should-contain "OK" (str *out*)))

    (it "returns 1 and prints errors when validation fails"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:defaults {:crew :ghost :model :llama}})
      (should= 1 (sut/run {:home test-home} ["validate"]))
      (should-contain "defaults.crew" (str *err*)))

    (it "overlays stdin content as a config file when validating"
      (write-config! (str test-home "/.isaac/config/crew/marvin.edn") {:model :llama})
      (binding [*in* (BufferedReader. (StringReader. "{:defaults {:crew :main :model :llama} :crew {:main {}} :models {:llama {:model \"llama3.3:1b\" :provider :anthropic}} :providers {:anthropic {}}}"))]
        (should= 0 (sut/run {:home test-home} ["validate" "--as" "isaac.edn" "-"]))
        (should-contain "OK" (str *out*)))))

  (describe "get"

    (it "prints scalar values by dotted path"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:crew {:marvin {:soul "You are Marvin."}}})
      (should= 0 (sut/run {:home test-home} ["get" "crew.marvin.soul"]))
      (should-contain "You are Marvin." (str *out*)))

    (it "prints scalar values by bracket keyword path"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:crew {:marvin {:soul "You are Marvin."}}})
      (should= 0 (sut/run {:home test-home} ["get" "crew[:marvin].soul"]))
      (should-contain "You are Marvin." (str *out*)))

    (it "returns 1 for a missing key"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:crew {:marvin {:soul "You are Marvin."}}})
      (should= 1 (sut/run {:home test-home} ["get" "crew.marvin.nope"]))
      (should-contain "not found: crew.marvin.nope" (str *err*))))

    (it "rejects the old --sources flag"
      (write-config! (str test-home "/.isaac/config/isaac.edn") {:crew {:main {}}})
      (should= 1 (sut/run {:home test-home} ["--sources"]))
      (should-contain "Unknown option: \"--sources\"" (str *err*)))

    (it "prints nested values across multiple lines"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                      {:defaults {:crew :main :model :llama}
                       :crew {:main {}
                              :marvin {:model :llama :soul "You are Marvin."}}
                       :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                       :providers {:anthropic {}}})
      (should= 0 (sut/run {:home test-home} ["get" "crew.marvin"]))
      (should (<= 2 (count (str/split-lines (str *out*))))))


    (it "reveals get values after typed confirmation and prompts first"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["get" "providers.anthropic.api-key" "--reveal"])))
      (should-contain "type REVEAL to confirm:" (str *err*))
      (should-contain "sk-test-123" (str *out*)))

    (it "refuses get reveal after a non-matching confirmation line"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "blah\n"))]
        (should= 1 (sut/run {:home test-home} ["get" "providers" "--reveal"])))
      (should= 1 (count (re-seq #"type REVEAL to confirm:" (str *err*))))
      (should-contain "Refusing to reveal config." (str *err*))
      (should-not-contain "sk-test-123" (str *out*)))

  )

  (describe "sources"

    (it "lists the config files that contributed"
      (binding [*out* (StringWriter.)
                *err* (StringWriter.)
                *in*  (BufferedReader. (StringReader. ""))
                fs/*fs* (fs/mem-fs)]
        (reset! c3env/-overrides {})
        (write-config! (str test-home "/.isaac/config/isaac.edn") {:crew {:main {}}})
        (write-config! (str test-home "/.isaac/config/crew/marvin.edn") {:model :llama})
        (should= 0 (sut/run {:home test-home} ["sources"]))
        (should-contain "config/isaac.edn" (str *out*))
        (should-contain "config/crew/marvin.edn" (str *out*)))))

  (describe "schema"

    (it "prints the root schema when no path is given"
      (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema"])))]
        (should-contain "Crew member configurations" output)
        (should-contain "Default crew and model selections" output)))

    (it "prints nested schema details for --all"
      (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema" "--all"])))]
        (should-contain "Crew member id; must match filename when present" output)
        (should-contain "base-url" output)))

    (it "prints a leaf schema by path"
      (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema" "providers[*].api-key"])))]
        (should-contain "type  string" output)
        (should-contain "API key" output)))

    (it "accepts shell-safe wildcard sentinel paths for schema lookup"
      (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema" "providers._.api-key"])))]
        (should-contain "API key" output)
        (should-contain "providers._.api-key" output)))

    (it "returns 1 for an unknown schema path"
      (let [err (StringWriter.)]
        (binding [*err* err]
          (should= 1 (sut/run {:home test-home} ["schema" "crew.nope"])))
        (should-contain "Path not found in config schema: crew.nope" (str err)))))

  (describe "help text"

    (it "lists set and unset subcommands"
      (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["--help"])))]
        (should-contain "set <path> <value> Set a value at a dotted path" output)
        (should-contain "set <path> -       Read EDN value from stdin" output)
        (should-contain "unset <path>       Remove a value at a dotted path" output))))

  (describe "registry integration"

    (it "registers the config command"
      (should-not-be-nil (registry/get-command "config"))))
