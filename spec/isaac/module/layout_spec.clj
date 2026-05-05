(ns isaac.module.layout-spec
  (:require
    [clojure.java.io :as io]
    [speclj.core :refer :all]))

(describe "module source layout"

  (it "keeps telly source under its module directory"
    (let [module-source  (io/file "modules/isaac.comm.telly/src/isaac/comm/telly.clj")
          plugin-source  (io/file "plugins/isaac/comm/telly.clj")]
      (should (.exists module-source))
      (should-not (.exists plugin-source))))

  (it "stores module manifests under resources and ships deps.edn"
    (doseq [module-dir ["modules/isaac.comm.discord" "modules/isaac.comm.telly"]]
      (should (.exists (io/file module-dir "deps.edn")))
      (should (.exists (io/file module-dir "resources/isaac-manifest.edn"))))))
