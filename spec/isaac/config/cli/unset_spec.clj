(ns isaac.config.cli.unset-spec
  (:require
    [isaac.config.cli.command :as sut]
    [isaac.config.mutate :as mutate]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io BufferedReader StringReader StringWriter)))

(def ^:private test-home "/test/config-unset")

(describe "CLI Config unset"

  (around [it]
    (binding [*out* (StringWriter.)
              *err* (StringWriter.)
              *in*  (BufferedReader. (StringReader. ""))
              fs/*fs* (fs/mem-fs)]
      (it)))

  (it "prints help and returns 0 with unset --help"
    (let [output (with-out-str (should= 0 (sut/run {:home test-home} ["unset" "--help"])))]
      (should-contain "Usage: isaac config" output)))

  (it "returns 1 when unset is missing a path"
    (let [err (StringWriter.)]
      (binding [*err* err]
        (should= 1 (sut/run {:home test-home} ["unset"])))
      (should-contain "missing path" (str err))))

  (it "treats trailing tokens after the path as arguments, not help options"
    (let [captured (atom nil)]
      (with-redefs [mutate/unset-config (fn [_home path]
                                          (reset! captured path)
                                          {:status :ok :warnings [] :file "isaac.edn"})]
        (should= 0 (sut/run {:home test-home} ["unset" "crew.marvin.soul" "--help"])))
      (should= "crew.marvin.soul" @captured))))
