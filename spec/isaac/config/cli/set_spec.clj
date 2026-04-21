(ns isaac.config.cli.set-spec
  (:require
    [isaac.config.cli.command :as sut]
    [isaac.config.mutate :as mutate]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io BufferedReader StringReader StringWriter)))

(def ^:private test-home "/test/config-set")

(describe "CLI Config set"

  (around [it]
    (binding [*out* (StringWriter.)
              *err* (StringWriter.)
              *in*  (BufferedReader. (StringReader. ""))
              fs/*fs* (fs/mem-fs)]
      (it)))

  (it "prints help and returns 0 with set --help"
    (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["set" "--help"])))]
      (should-contain "Usage: isaac config" output)))

  (it "returns 1 when set is missing a path"
    (let [err (StringWriter.)]
      (binding [*err* err]
        (should= 1 (sut/run {:home test-home} ["set"])))
      (should-contain "missing path" (str err))))

  (it "returns 1 when set is missing a value"
    (let [err (StringWriter.)]
      (binding [*err* err]
        (should= 1 (sut/run {:home test-home} ["set" "defaults.crew"])))
      (should-contain "missing value" (str err))))

  (it "treats a hyphen-prefixed token as the set value after the path"
    (let [captured (atom nil)]
      (with-redefs [mutate/set-config (fn [_home path value]
                                        (reset! captured [path value])
                                        {:status :ok :warnings [] :file "isaac.edn"})]
        (should= 0 (sut/run {:home test-home} ["set" "crew.marvin.soul" "--raw"])))
      (should= ["crew.marvin.soul" "--raw"] @captured))))
