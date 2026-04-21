(ns isaac.config.cli.validate-spec
  (:require
    [isaac.config.cli.command :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all])
  (:import (java.io BufferedReader StringReader StringWriter)))

(def ^:private test-home "/test/config-validate")

(defn- write-config! [path data]
  (fs/mkdirs (fs/parent path))
  (fs/spit path (pr-str data)))

(describe "CLI Config validate"

  (around [it]
    (binding [*out* (StringWriter.)
              *err* (StringWriter.)
              *in*  (BufferedReader. (StringReader. ""))
              fs/*fs* (fs/mem-fs)]
      (it)))

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

  (it "overlays stdin content at a data path when validating"
    (write-config! (str test-home "/.isaac/config/isaac.edn")
                   {:defaults  {:crew :main :model :llama}
                    :crew      {}
                    :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
                    :providers {:anthropic {}}})
    (binding [*in* (BufferedReader. (StringReader. "{:soul \"You are Isaac.\"}"))]
      (let [result (sut/run {:home test-home} ["validate" "--as" "crew.main" "-"])]
        (should= 0 result))
      (should-contain "OK" (str *out*)))))
