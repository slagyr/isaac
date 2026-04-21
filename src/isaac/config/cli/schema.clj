(ns isaac.config.cli.schema
  "isaac config schema — print the schema for a schema path."
  (:require
    [isaac.config.cli.common :as common]
    [isaac.config.schema :as config-schema]
    [isaac.config.schema.term :as schema-term]))

(def option-spec
  [[nil  "--tree" "Expand every named sub-schema as its own section"]
   ["-h" "--help" "Show help"]])

(defn help []
  (common/render-help
    {:command     "isaac config schema"
     :params      "[schema-path] [options]"
     :description (str "Print the config schema for a schema path. Schema paths use literal\n"
                       "'key' and 'value' segments to address the key/value types of a map —\n"
                       "for example 'crew.value' is the schema of a single crew entry,\n"
                       "'crew.value.soul' drills into the soul field on that entry.")
     :option-spec option-spec
     :examples    (str "  isaac config schema\n"
                       "  isaac config schema crew\n"
                       "  isaac config schema providers.value.api-key\n"
                       "  isaac config schema --tree")}))

(defn- guidance []
  (str "\nTry:\n"
       "  isaac config schema crew\n"
       "  isaac config schema providers.value\n"
       "  isaac config schema crew.value.model"))

(defn- print-schema! [path-str tree?]
  (if-let [spec (config-schema/schema-for-path path-str)]
    (let [root?  (or (nil? path-str) (clojure.string/blank? path-str))
          output (schema-term/spec->term spec {:color?      (common/stdout-tty?)
                                               :path-prefix (common/path-prefix path-str)
                                               :deep?       (boolean tree?)
                                               :width       80})]
      (println (if root? (str output (guidance)) output))
      0)
    (do
      (binding [*out* *err*]
        (println (str "Path not found in config schema: " path-str)))
      1)))

(defn run [_opts arguments options]
  (print-schema! (common/normalize-path (first arguments)) (:tree options)))

(def subcommand
  {:option-spec option-spec
   :runner      run
   :help-text   help})
