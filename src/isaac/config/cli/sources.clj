(ns isaac.config.cli.sources
  "isaac config sources — list contributing config files."
  (:require [isaac.config.cli.common :as common]))

(defn help []
  (str "Usage: isaac config sources\n\n"
       "List every config file that contributes to the resolved config,\n"
       "in the order they are applied.\n\n"
       "Options:\n"
       "  -h, --help        Show help"))

(defn run [opts _arguments _options]
  (let [{:keys [sources]} (common/load-result opts)]
    (common/print-lines! sources)
    0))

(def subcommand
  {:option-spec common/help-option-spec
   :runner      run
   :help-text   help})
