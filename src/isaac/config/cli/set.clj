(ns isaac.config.cli.set
  "isaac config set — set a value at a config path."
  (:require
    [clojure.string :as str]
    [isaac.config.cli.common :as common]
    [isaac.config.cli.mutate-common :as mutate-common]))

(defn help []
  (common/render-help
    {:command     "isaac config set"
     :params      "<config-path> <value|->"
     :description (str "Set a config value at a config path. Writes to the entity file when the\n"
                       "key already lives in one; otherwise writes to the root isaac.edn file\n"
                       "(or a new entity file when [prefer-entity-files] is true).")
     :arguments   (str "  <config-path>     Config path (e.g. crew.marvin.model)\n"
                       "  <value>           Scalar value; keywords, numbers, and strings are inferred\n"
                       "  -                 Read the value as EDN from stdin")
     :option-spec common/help-option-spec
     :examples    (str "  isaac config set crew.marvin.model llama\n"
                       "  echo '{:soul \"paranoid\"}' | isaac config set crew.marvin -")}))

(defn run [opts arguments _options]
  (cond
    (str/blank? (first arguments)) (common/print-cli-error! "missing path")
    (nil? (second arguments))      (common/print-cli-error! "missing value")
    :else                          (mutate-common/set-config! (common/home-dir opts)
                                                              (common/normalize-path (first arguments))
                                                              (second arguments))))

(def subcommand
  {:option-spec common/help-option-spec
   :parse-args  [:in-order true]
   :runner      run
   :help-text   help})
