(ns isaac.config.cli.unset
  "isaac config unset — remove a value at a config path."
  (:require
    [clojure.string :as str]
    [isaac.config.cli.common :as common]
    [isaac.config.cli.mutate-common :as mutate-common]))

(defn help []
  (common/render-help
    {:command     "isaac config unset"
     :params      "<config-path>"
     :description (str "Remove a value at a config path. Deletes the key from whichever file\n"
                       "defines it; deletes the entity file entirely if unset empties it.")
     :option-spec common/help-option-spec
     :examples    "  isaac config unset crew.marvin.soul"}))

(defn run [opts arguments _options]
  (if (str/blank? (first arguments))
    (common/print-cli-error! "missing path")
    (mutate-common/unset-config! (common/home-dir opts) (common/normalize-path (first arguments)))))

(def subcommand
  {:option-spec common/help-option-spec
   :parse-args  [:in-order true]
   :runner      run
   :help-text   help})
