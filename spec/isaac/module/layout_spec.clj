(ns isaac.module.layout-spec
  (:require
    [clojure.java.io :as io]
    [speclj.core :refer :all]))

(describe "module source layout"

  (it "keeps telly source under its module directory"
    (let [module-source  (io/file "modules/isaac.comm.telly/src/isaac/comm/telly.clj")
          plugin-source  (io/file "plugins/isaac/comm/telly.clj")]
      (should (.exists module-source))
      (should-not (.exists plugin-source)))))
