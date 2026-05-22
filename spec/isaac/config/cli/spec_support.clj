(ns isaac.config.cli.spec-support
  (:require
    [isaac.fs :as fs]
    [isaac.system :as system])
  (:import (java.io BufferedReader StringReader StringWriter)))

(defn with-cli-env [f]
  (system/with-nested-system {:fs (fs/mem-fs)}
    (binding [*out*  (StringWriter.)
              *err*  (StringWriter.)
              *in*   (BufferedReader. (StringReader. ""))]
      (f))))
