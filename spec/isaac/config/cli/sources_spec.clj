(ns isaac.config.cli.sources-spec
  (:require
    [c3kit.apron.env :as c3env]
    [isaac.config.cli.command :as sut]
    [isaac.config.cli.spec-support :as support]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def ^:private test-home "/test/config-sources")

(defn- write-config! [path data]
  (fs/mkdirs (fs/parent path))
  (fs/spit path (pr-str data)))

(describe "CLI Config sources"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (support/with-cli-env #(do (reset! c3env/-overrides {})
                               (example))))

  (it "lists the config files that contributed"
    (write-config! (str test-home "/.isaac/config/isaac.edn") {:crew {:main {}}})
    (write-config! (str test-home "/.isaac/config/crew/marvin.edn") {:model :llama})
    (should= 0 (sut/run {:home test-home} ["sources"]))
    (should-contain "config/isaac.edn" (str *out*))
    (should-contain "config/crew/marvin.edn" (str *out*))))
