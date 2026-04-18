(ns isaac.cli.config-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.cli.config :as sut]
    [isaac.cli.registry :as registry]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-home "/test/config-cli")

(defn- write-config! [path data]
  (fs/spit path (pr-str data)))

(describe "CLI Config"

  (around [it]
    (binding [*out* (java.io.StringWriter.)
              *err* (java.io.StringWriter.)
              *in*  (java.io.BufferedReader. (java.io.StringReader. ""))
              fs/*fs* (fs/mem-fs)]
      (reset! c3env/-overrides {})
      (it)))

  (describe "run"

    (it "prints help and returns 0 with --help"
      (should= 0 (sut/run {:home test-home} ["--help"])))

    (it "prints resolved config with env values redacted by default"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:apiKey "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} []))
      (should-contain "<CONFIG_TEST_API_KEY:redacted>" (str *out*))
      (should-not-contain "sk-test-123" (str *out*)))

    (it "prints raw config without substitution"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:apiKey "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} ["--raw"]))
      (should-contain "${CONFIG_TEST_API_KEY}" (str *out*))
      (should-not-contain "redacted" (str *out*)))

    (it "reveals actual values only after typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:apiKey "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["--reveal"])))
      (should-contain "sk-test-123" (str *out*)))

    (it "refuses reveal without typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:apiKey "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 1 (sut/run {:home test-home} ["--reveal"]))
      (should-contain "type REVEAL to confirm" (str *err*))
      (should-not-contain "sk-test-123" (str *out*))))

  (describe "validate"

    (it "prints OK and returns 0 when validation passes"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:defaults {:crew :main :model :llama}
                      :crew {:main {:soul "You are Isaac."}}})
      (should= 0 (sut/run {:home test-home} ["validate"]))
      (should-contain "OK" (str *out*)))

    (it "returns 1 and prints errors when validation fails"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:defaults {:crew :ghost :model :llama}})
      (should= 1 (sut/run {:home test-home} ["validate"]))
      (should-contain "defaults.crew" (str *err*)))

    (it "overlays stdin content as a config file when validating"
      (write-config! (str test-home "/.isaac/config/crew/marvin.edn") {:model :llama})
      (binding [*in* (java.io.BufferedReader. (java.io.StringReader. "{:defaults {:crew :main :model :llama} :crew {:main {}}}"))]
        (should= 0 (sut/run {:home test-home} ["validate" "--as" "isaac.edn" "-"]))
        (should-contain "OK" (str *out*)))))

  (describe "get"

    (it "prints scalar values by dotted path"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:crew {:marvin {:soul "You are Marvin."}}})
      (should= 0 (sut/run {:home test-home} ["get" "crew.marvin.soul"]))
      (should-contain "You are Marvin." (str *out*)))

    (it "returns 1 for a missing key"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:crew {:marvin {:soul "You are Marvin."}}})
      (should= 1 (sut/run {:home test-home} ["get" "crew.marvin.nope"]))
      (should-contain "not found: crew.marvin.nope" (str *err*))))

  (describe "sources"

    (it "lists the config files that contributed"
      (write-config! (str test-home "/.isaac/config/isaac.edn") {:crew {:main {}}})
      (write-config! (str test-home "/.isaac/config/crew/marvin.edn") {:model :llama})
      (should= 0 (sut/run {:home test-home} ["--sources"]))
      (should-contain "config/isaac.edn" (str *out*))
      (should-contain "config/crew/marvin.edn" (str *out*)))))

  (describe "registry integration"

    (it "registers the config command"
      (should-not-be-nil (registry/get-command "config"))))
