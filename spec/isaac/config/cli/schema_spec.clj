(ns isaac.config.cli.schema-spec
  (:require
    [isaac.config.cli.command :as sut]
    [isaac.config.cli.spec-support :as support]
    [speclj.core :refer :all])
  (:import (java.io StringWriter)))

(def ^:private test-home "/test/config-schema")

(describe "CLI Config schema"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (support/with-cli-env example))

  (it "prints the root schema when no path is given"
    (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema"])))]
      (should-contain "Crew member configurations" output)
      (should-contain "Default crew and model selections" output)))

  (it "resolves .value paths through a collection map's value-spec"
    (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema" "providers.value.api-key"])))]
      (should-contain "string" output)
      (should-contain "API key" output)
      (should-contain "providers.value.api-key" output)))

  (it "resolves .key paths to the collection map's key-spec"
    (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["schema" "providers.key"])))]
      (should-contain "string" output)
      (should-contain "providers.key" output)))

  (it "returns 1 for an unknown schema path"
    (let [err (StringWriter.)]
      (binding [*err* err]
        (should= 1 (sut/run {:home test-home} ["schema" "crew.nope"])))
      (should-contain "Path not found in config schema: crew.nope" (str err)))))
