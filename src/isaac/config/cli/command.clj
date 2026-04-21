(ns isaac.config.cli.command
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [clojure.walk :as walk]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [c3kit.apron.schema.path :as path]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as loader]
    [isaac.config.mutate :as mutate]
    [isaac.config.schema :as config-schema]
    [isaac.config.schema.term :as schema-term]
    [isaac.logger :as log]))

;; region ----- Helpers -----

(def ^:private option-spec
  [[nil  "--raw"     "Print pre-substitution config"]
   [nil  "--reveal"  "Reveal secrets after confirmation"]
   ["-h" "--help"    "Show help"]])

(def ^:private validate-option-spec
  [[nil  "--as PATH" "Overlay stdin at a config path before validating"]
   ["-h" "--help"   "Show help"]])

(def ^:private get-option-spec
  [[nil  "--reveal" "Reveal secrets after confirmation"]
   ["-h" "--help"   "Show help"]])

(def ^:private schema-option-spec
  [[nil  "--tree" "Expand every named sub-schema as its own section"]
   ["-h" "--help" "Show help"]])

(def ^:private help-option-spec
  [["-h" "--help" "Show help"]])

(defn- parse-option-map [args option-spec & parse-args]
  (let [{:keys [arguments errors options]} (apply tools-cli/parse-opts args option-spec parse-args)]
    {:arguments arguments
     :errors    errors
     :options   (->> options
                     (remove (comp nil? val))
                     (into {}))}))

(defn- home-dir [{:keys [home state-dir]}]
  (or home state-dir (System/getProperty "user.home")))

(defn- keyword-safe-segment? [s]
  (and (not (str/blank? s))
       (some? (re-matches #"[A-Za-z*+!_?-][A-Za-z0-9*+!_?-]*" s))))

(defn- segment->expr [first? seg]
  (cond
    (and first? (keyword-safe-segment? seg)) seg
    (keyword-safe-segment? seg)              (str "." seg)
    :else                                    (str "[\"" seg "\"]")))

(defn- normalize-path
  "When path-str begins with '/', treat '/' as the only separator and '.' as a
   literal character inside each segment. Otherwise return path-str unchanged.
   Emits a path-str that c3kit.apron.schema.path parses equivalently to the
   user's intent — simple segments stay bare, segments with non-keyword chars
   are wrapped in bracket-string form."
  [path-str]
  (if (and (string? path-str) (str/starts-with? path-str "/"))
    (let [segs (remove str/blank? (str/split (subs path-str 1) #"/"))]
      (apply str (map-indexed (fn [idx s] (segment->expr (zero? idx) s)) segs)))
    path-str))

(defn- print-lines! [lines]
  (doseq [line lines]
    (println line)))

(defn- print-edn! [value]
  (if (coll? value)
    (binding [pprint/*print-right-margin* 20]
      (pprint/pprint value))
    (pprint/pprint value)))

(defn- print-errors! [entries label]
  (binding [*out* *err*]
    (doseq [{:keys [key value]} entries]
      (println (str label ": " key " - " value)))))

(defn- print-warnings! [entries]
  (binding [*out* *err*]
    (doseq [{:keys [key value]} entries]
      (println (str "warning: :" key " - " value)))))

(defn- reveal-confirmed? []
  (binding [*out* *err*]
    (print "type REVEAL to confirm: ")
    (flush))
  (= "REVEAL" (some-> (read-line) str/trim)))

(defn- print-reveal-refused! []
  (binding [*out* *err*]
    (println "Refusing to reveal config.")))

(defn- env-token [value]
  (when (and (string? value)
             (re-matches #"\$\{[^}]+\}" value))
    (second (re-matches #"\$\{([^}]+)\}" value))))

(defn- redact-env-values [raw resolved]
  (cond
    (and (map? raw) (map? resolved))
    (into {} (map (fn [k] [k (redact-env-values (get raw k) (get resolved k))]) (set (concat (keys raw) (keys resolved)))))

    (and (sequential? raw) (sequential? resolved))
    (mapv redact-env-values raw resolved)

    :else
    (if-let [token (env-token raw)]
      (str "<" token ":" (if (= raw resolved) "UNRESOLVED" "redacted") ">")
      resolved)))

(defn- resolve-env-values [value]
  (cond
    (map? value)        (into {} (map (fn [[k v]] [k (resolve-env-values v)]) value))
    (sequential? value) (mapv resolve-env-values value)
    :else               (if-let [token (env-token value)]
                          (or (loader/env token) value)
                          value)))

(defn- queryable-config [config]
  (walk/postwalk
    (fn [node]
      (if (map? node)
        (into {} (map (fn [[k v]] [(if (string? k) (keyword k) k) v]) node))
        node))
    config))

(defn- value-present? [value]
  (not (nil? value)))

(defn- present-identifiers [value]
  (walk/postwalk
    (fn [node]
      (if (map? node)
        (cond-> node
          (string? (:crew node))     (assoc :crew (keyword (:crew node)))
          (string? (:model node))    (assoc :model (keyword (:model node)))
          (string? (:provider node)) (assoc :provider (keyword (:provider node))))
        node))
    value))

(defn- load-result [opts]
  (loader/load-config-result {:home (home-dir opts)}))

(defn- load-raw-result [opts]
  (loader/load-config-result {:home (home-dir opts) :substitute-env? false}))

(defn- printable-config [opts reveal?]
  (let [raw      (load-raw-result opts)
        resolved (assoc raw :config (resolve-env-values (:config raw)))]
    (if reveal?
      resolved
      (assoc resolved :config (redact-env-values (:config raw) (:config resolved))))))

(defn- config-help []
  (str "Usage: isaac config [subcommand] [options]\n\n"
       "Manage Isaac configuration\n\n"
       "Subcommands:\n"
       "  get <config-path>         Get a value by config path\n"
       "  help <subcommand>         Print usage details on a subcommand\n"
       "  schema [schema-path]      Print the config schema for a schema path\n"
       "  set <config-path> <value> Set a value at a config path\n"
       "  sources                   List contributing config files\n"
       "  unset <config-path>       Remove a value at a config path\n"
       "  validate                  Validate config\n\n"
       "Options:\n"
       "      --raw                 Print pre-substitution config\n"
       "      --reveal              Reveal secrets after confirmation\n"
       "  -h, --help                Show help\n\n"
       "Paths:\n"
       "  config path  addresses a value in the resolved config\n"
       "               e.g. crew.marvin.soul, providers.anthropic.api-key\n"
       "  schema path  addresses a node in the schema tree, using literal\n"
       "               'key' and 'value' segments for map key/value types\n"
       "               e.g. crew.value.soul, providers.value.api-key\n\n"
       "  Separators:\n"
       "    default      '.' splits segments. Brackets are used for unfriendly keys.\n"
       "                 e.g. crew[\"Almighty Bob\"].model\n"
       "    slash-mode   Lead with a / for the shell-friendly / separator.\n"
       "                 e.g. /crew/Almighty Bob/model\n\n"))

(defn- get-help []
  (str "Usage: isaac config get <config-path> [options]\n\n"
       "Read a value from the resolved config by config path.\n\n"
       "Options:\n"
       "      --reveal      Reveal ${VAR} secrets after confirmation (type REVEAL on stdin)\n"
       "  -h, --help        Show help\n\n"
       "Examples:\n"
       "  isaac config get crew.marvin.soul\n"
       "  isaac config get providers.anthropic.api-key --reveal"))

(defn- set-help []
  (str "Usage: isaac config set <config-path> <value|->\n\n"
       "Set a config value at a config path. Writes to the entity file when the\n"
       "key already lives in one; otherwise writes to the root isaac.edn file\n"
       "(or a new entity file when [prefer-entity-files] is true).\n\n"
       "Arguments:\n"
       "  <config-path>     Config path (e.g. crew.marvin.model)\n"
       "  <value>           Scalar value; keywords, numbers, and strings are inferred\n"
       "  -                 Read the value as EDN from stdin\n\n"
       "Options:\n"
       "  -h, --help        Show help\n\n"
       "Examples:\n"
       "  isaac config set crew.marvin.model llama\n"
       "  echo '{:soul \"paranoid\"}' | isaac config set crew.marvin -"))

(defn- unset-help []
  (str "Usage: isaac config unset <config-path>\n\n"
       "Remove a value at a config path. Deletes the key from whichever file\n"
       "defines it; deletes the entity file entirely if unset empties it.\n\n"
       "Options:\n"
       "  -h, --help        Show help\n\n"
       "Example:\n"
       "  isaac config unset crew.marvin.soul"))

(defn- schema-help []
  (str "Usage: isaac config schema [schema-path] [options]\n\n"
       "Print the config schema for a schema path. Schema paths use literal\n"
       "'key' and 'value' segments to address the key/value types of a map —\n"
       "for example 'crew.value' is the schema of a single crew entry,\n"
       "'crew.value.soul' drills into the soul field on that entry.\n\n"
       "Options:\n"
       "      --tree        Expand every named sub-schema as its own section\n"
       "  -h, --help        Show help\n\n"
       "Examples:\n"
       "  isaac config schema\n"
       "  isaac config schema crew\n"
       "  isaac config schema providers.value.api-key\n"
       "  isaac config schema --tree"))

(defn- sources-help []
  (str "Usage: isaac config sources\n\n"
       "List every config file that contributes to the resolved config,\n"
       "in the order they are applied.\n\n"
       "Options:\n"
       "  -h, --help        Show help"))

(defn- validate-help []
  (str "Usage: isaac config validate [options] [-]\n\n"
       "Validate the config composition\n\n"
       "Options:\n"
       "      --as <config-path>  Overlay stdin EDN at the given config path before validating\n"
       "  -h, --help              Show help\n\n"
       "Arguments:\n"
       "  -                       Read EDN to validate from stdin (isolated when no --as)"))

(defn- print-config! [opts]
  (let [{:keys [config errors warnings]} (printable-config opts false)]
    (print-errors! errors "error")
    (print-warnings! warnings)
    (if (seq errors)
      1
      (do
        (print-edn! (present-identifiers config))
        0))))

(defn- print-raw-config! [opts]
  (let [{:keys [config errors warnings]} (load-raw-result opts)]
    (print-errors! errors "error")
    (print-warnings! warnings)
    (if (seq errors)
      1
      (do
        (print-edn! (present-identifiers config))
        0))))

(defn- print-revealed-config! [opts]
  (if-not (reveal-confirmed?)
    (do
      (print-reveal-refused!)
      1)
    (let [{:keys [config errors warnings]} (printable-config opts true)]
      (print-errors! errors "error")
      (print-warnings! warnings)
      (if (seq errors)
        1
        (do
          (print-edn! (present-identifiers config))
          0)))))

(defn- print-sources! [opts]
  (let [{:keys [sources]} (load-result opts)]
    (print-lines! sources)
    0))

(def ^:private entity-collections #{:crew :models :providers})

(defn- parse-data-path [path-str]
  (let [segments (str/split path-str #"\.")
        head     (keyword (first segments))
        entity?  (contains? entity-collections head)
        tail     (cond->> (rest segments)
                   entity? (map-indexed (fn [idx seg] (if (zero? idx) seg (keyword seg))))
                   (not entity?) (map keyword))]
    (into [head] tail)))

(defn- report-validation! [{:keys [errors warnings]}]
  (print-errors! errors "error")
  (print-warnings! warnings)
  (if (seq errors)
    1
    (do
      (println "OK - config is valid")
      0)))

(defn- validate-stdin! [opts]
  (report-validation!
    (loader/load-config-result {:home               (home-dir opts)
                                :overlay-content    (slurp *in*)
                                :overlay-path       "isaac.edn"
                                :skip-entity-files? true})))

(defn- validate-overlay-data! [opts data-path-str]
  (let [stdin-value (try
                      {:value (edn/read-string (slurp *in*))}
                      (catch Exception e
                        {:error (.getMessage e)}))]
    (if (:error stdin-value)
      (do
        (binding [*out* *err*]
          (println (str "invalid EDN from stdin: " (:error stdin-value))))
        1)
      (report-validation!
        (loader/load-config-result {:home              (home-dir opts)
                                    :data-path-overlay {:path  (parse-data-path data-path-str)
                                                        :value (:value stdin-value)}})))))

(defn- validate-config! [opts]
  (report-validation! (load-result opts)))

(defn- get-value! [opts path reveal?]
  (let [{:keys [config errors missing-config?]} (if reveal?
                                                   (printable-config opts true)
                                                   (printable-config opts false))]
    (cond
      missing-config?
      (do
        (print-errors! errors "error")
        1)

      (and reveal? (not (reveal-confirmed?)))
      (do
        (print-reveal-refused!)
        1)

      :else
      (let [queryable (queryable-config config)
            value     (path/data-at queryable path)]
        (if (value-present? value)
          (do
            (print-edn! (present-identifiers value))
            0)
          (do
            (binding [*out* *err*]
              (println (str "not found: " path)))
            1))))))

(defn- stdout-tty? []
  (and (some? (System/console))
       (not (instance? java.io.StringWriter *out*))))

(defn- schema-guidance []
  (str "\nTry:\n"
       "  isaac config schema crew\n"
       "  isaac config schema providers.value\n"
       "  isaac config schema crew.value.model"))

(defn- path-prefix [path-str]
  (when-not (or (nil? path-str) (str/blank? path-str))
    (vec (str/split path-str #"\."))))

(defn- print-schema! [path-str tree?]
  (if-let [spec (config-schema/schema-for-path path-str)]
    (let [root?  (or (nil? path-str) (str/blank? path-str))
          output (schema-term/spec->term spec {:color?      (stdout-tty?)
                                               :path-prefix (path-prefix path-str)
                                               :deep?       (boolean tree?)
                                               :width       80})]
      (println (if root? (str output (schema-guidance)) output))
      0)
    (do
      (binding [*out* *err*]
        (println (str "Path not found in config schema: " path-str)))
      1)))

(defn- target-spec-for [path-str]
  (config-schema/schema-for-data-path path-str))

(defn- parse-set-value [spec raw-value]
  (cond
    (re-matches #"-?\d+" raw-value)
    (parse-long raw-value)

    (#{"false" "nil" "true"} raw-value)
    (edn/read-string raw-value)

    (str/starts-with? raw-value ":")
    (edn/read-string raw-value)

    (and spec (:coerce spec) (re-matches #"[A-Za-z_][A-Za-z0-9_-]*" raw-value))
    (keyword raw-value)

    :else
    raw-value))

(defn- read-stdin-value []
  (try
    {:value (edn/read-string (slurp *in*))}
    (catch Exception _
      {:error "stdin must contain valid EDN"})))

(defn- format-errors [errors]
  (str/join "; " (map (fn [{:keys [key value]}] (str key " - " value)) errors)))

(defn- log-mutation! [level event file path-str & kvs]
  (apply log/log* level event file 0 :path path-str kvs))

(defn- print-status-error! [status path-str]
  (binding [*out* *err*]
    (println (case status
               :missing-path      "missing path"
               :missing-entity-id "missing entity id"
               :invalid-path      (str "invalid path: " path-str)
               :not-found         (str "not found: " path-str)
               (str "config error: " (name status))))))

(defn- handle-mutate-result! [operation path-str result value]
  (print-warnings! (:warnings result))
  (case (:status result)
    :ok
    (do
      (case operation
        :set   (log-mutation! :info :config/set   (:file result) path-str :value value)
        :unset (log-mutation! :info :config/unset (:file result) path-str))
      0)

    :invalid
    (do
      (print-errors! (:errors result) "error")
      (when (= :set operation)
        (log-mutation! :error :config/set-failed "config" path-str :error (format-errors (:errors result))))
      1)

    :invalid-config
    (do
      (print-errors! (:errors result) "error")
      1)

    (do
      (print-status-error! (:status result) path-str)
      1)))

(defn- mutate-config! [opts operation path-str raw-value]
  (case operation
    :set
    (let [value-result (if (= "-" raw-value)
                         (read-stdin-value)
                         {:value (parse-set-value (target-spec-for path-str) raw-value)})]
      (if (:error value-result)
        (do
          (binding [*out* *err*]
            (println (:error value-result)))
          (log-mutation! :error :config/set-failed "config" path-str :error (:error value-result))
          1)
        (let [value  (:value value-result)
              result (mutate/set-config (home-dir opts) path-str value)]
          (handle-mutate-result! operation path-str result value))))

    :unset
    (handle-mutate-result! operation path-str (mutate/unset-config (home-dir opts) path-str) nil)))

(defn- print-subcommand-help! [help-fn]
  (println (if help-fn (help-fn) (config-help)))
  0)

(defn- print-help! []
  (println (config-help))
  0)

(defn- print-cli-errors! [errors]
  (binding [*out* *err*]
    (doseq [error errors]
      (println error)))
  1)

(defn- print-cli-error! [message]
  (binding [*out* *err*]
    (println message))
  1)

(defn- run-parsed-subcommand [opts sub-args {:keys [option-spec parse-args runner help-text]}]
  (let [{:keys [arguments errors options]} (apply parse-option-map sub-args option-spec parse-args)]
    (cond
      (:help options) (print-subcommand-help! help-text)
      (seq errors)    (print-cli-errors! errors)
      :else           (runner opts arguments options))))

(defn- run-default [opts args]
  (let [{:keys [arguments errors options]} (parse-option-map args option-spec :in-order true)]
    (cond
      (seq errors)      (print-cli-errors! errors)
      (:help options)   (print-help!)
      (:raw options)    (print-raw-config! opts)
      (:reveal options) (print-revealed-config! opts)
      (seq arguments)   (print-cli-error! (str "Unknown config subcommand: " (first arguments)))
      :else             (print-config! opts))))

(defn- file-path-style? [path-str]
  (and path-str
       (not (str/starts-with? path-str "/"))
       (str/includes? path-str "/")))

(defn- run-validate [opts arguments options]
  (let [as-value (:as options)
        stdin?   (= "-" (first arguments))]
    (cond
      (file-path-style? as-value)
      (print-cli-error! (str "validate --as expected a config path like foo.bar, got file path: " as-value))

      as-value
      (if stdin?
        (validate-overlay-data! opts (normalize-path as-value))
        (print-cli-error! "validate --as requires '-' stdin source"))

      stdin?
      (validate-stdin! opts)

      :else
      (validate-config! opts))))

(defn- run-sources [opts _arguments _options]
  (print-sources! opts))

(defn- run-get [opts arguments options]
  (if (str/blank? (first arguments))
    (print-cli-error! "missing path")
    (get-value! opts (normalize-path (first arguments)) (:reveal options))))

(defn- run-schema [_opts arguments options]
  (print-schema! (normalize-path (first arguments)) (:tree options)))

(defn- run-set [opts arguments _options]
  (cond
    (str/blank? (first arguments)) (print-cli-error! "missing path")
    (nil? (second arguments))      (print-cli-error! "missing value")
    :else                          (mutate-config! opts :set (normalize-path (first arguments)) (second arguments))))

(defn- run-unset [opts arguments _options]
  (if (str/blank? (first arguments))
    (print-cli-error! "missing path")
    (mutate-config! opts :unset (normalize-path (first arguments)) nil)))

(def ^:private subcommand->runner
  {"validate" {:option-spec validate-option-spec :runner run-validate :help-text validate-help}
   "sources"  {:option-spec help-option-spec     :runner run-sources  :help-text sources-help}
   "get"      {:option-spec get-option-spec      :runner run-get      :help-text get-help}
   "schema"   {:option-spec schema-option-spec   :runner run-schema   :help-text schema-help}
   "set"      {:option-spec help-option-spec     :parse-args [:in-order true] :runner run-set   :help-text set-help}
   "unset"    {:option-spec help-option-spec     :parse-args [:in-order true] :runner run-unset :help-text unset-help}})

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Entry Point -----

(defn run [opts args]
  (cond
    (and (= "help" (first args)) (get subcommand->runner (second args)))
    (print-subcommand-help! (:help-text (get subcommand->runner (second args))))

    :else
    (if-let [subcommand (get subcommand->runner (first args))]
      (run-parsed-subcommand opts (rest args) subcommand)
      (run-default opts args))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (run opts (or _raw-args [])))

(registry/register!
  {:name        "config"
   :usage       "config [subcommand] [options]"
   :desc        "Inspect and validate Isaac configuration"
   :help-text   config-help
   :option-spec option-spec
   :run-fn      run-fn})

;; endregion ^^^^^ Entry Point ^^^^^
