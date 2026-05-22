(ns isaac.config.cli.spec-support
  (:require
    [isaac.fs :as fs]
    [isaac.system :as system])
  (:import (java.io BufferedReader StringReader StringWriter)))

(defn with-cli-env [f]
  (let [mem (fs/mem-fs)]
    (system/with-nested-system {:fs mem}
      (binding [*out*  (StringWriter.)
                *err*  (StringWriter.)
                *in*   (BufferedReader. (StringReader. ""))
                fs/*fs* mem]
        (f)))))
