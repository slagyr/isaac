(ns isaac.config.cli.spec-support
  (:require
    [isaac.fs :as fs])
  (:import (java.io BufferedReader StringReader StringWriter)))

(defn with-cli-env [f]
  (binding [*out*  (StringWriter.)
            *err*  (StringWriter.)
            *in*   (BufferedReader. (StringReader. ""))
            fs/*fs* (fs/mem-fs)]
    (f)))
