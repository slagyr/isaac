(ns isaac.config.cli.get-spec
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [isaac.config.cli.command :as sut]
    [isaac.config.cli.spec-support :as support]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io BufferedReader StringReader)))

(def ^:private test-home "/test/config-get")

(defn- write-config! [path data]
  (fs/mkdirs (fs/parent path))
  (fs/spit path (pr-str data)))

(describe "CLI Config get"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (support/with-cli-env #(do (reset! c3env/-overrides {})
                               (example))))

  (describe "whole config"

    (it "prints the resolved config when no path is given, redacting env values"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} ["get"]))
      (should-contain "<CONFIG_TEST_API_KEY:redacted>" (str *out*))
      (should-not-contain "sk-test-123" (str *out*)))

    (it "prints raw config without substitution when --raw is set"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 0 (sut/run {:home test-home} ["get" "--raw"]))
      (should-contain "${CONFIG_TEST_API_KEY}" (str *out*))
      (should-not-contain "redacted" (str *out*)))

    (it "reveals actual values only after typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["get" "--reveal"])))
      (should-contain "type REVEAL to confirm:" (str *err*))
      (should-contain "sk-test-123" (str *out*)))

    (it "refuses reveal without typed confirmation"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (should= 1 (sut/run {:home test-home} ["get" "--reveal"]))
      (should-contain "Refusing to reveal config." (str *err*))
      (should-not-contain "sk-test-123" (str *out*))))

  (describe "subtree"

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
      (should-contain "not found: crew.marvin.nope" (str *err*)))

    (it "prints nested values across multiple lines"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:defaults {:crew :main :model :llama}
                      :crew {:main {}
                             :marvin {:model :llama :soul "You are Marvin."}}
                      :models {:llama {:model "llama3.3:1b" :provider :anthropic}}
                      :providers {:anthropic {}}})
      (should= 0 (sut/run {:home test-home} ["get" "crew.marvin"]))
      (should (<= 2 (count (str/split-lines (str *out*))))))

    (it "prints provider auth when configured"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:chatgpt {:auth "oauth-device"}}})
      (should= 0 (sut/run {:home test-home} ["get" "providers.chatgpt.auth"]))
      (should-contain "oauth-device" (str *out*)))

    (it "reveals get values after typed confirmation and prompts first"
      (write-config! (str test-home "/.isaac/config/isaac.edn")
                     {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}})
      (c3env/override! "CONFIG_TEST_API_KEY" "sk-test-123")
      (binding [*in* (BufferedReader. (StringReader. "REVEAL\n"))]
        (should= 0 (sut/run {:home test-home} ["get" "providers.anthropic.api-key" "--reveal"])))
      (should-contain "type REVEAL to confirm:" (str *err*))
      (should-contain "sk-test-123" (str *out*)))))
