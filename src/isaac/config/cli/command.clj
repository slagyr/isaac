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
  [[nil  "--as RELPATH" "Overlay stdin as a config file path"]
   ["-h" "--help"      "Show help"]])

(def ^:private get-option-spec
  [[nil  "--reveal" "Reveal secrets after confirmation"]
   ["-h" "--help"   "Show help"]])

(def ^:private schema-option-spec
  [[nil  "--all"  "Expand nested schema sections"]
   ["-h" "--help" "Show help"]])

(def ^:private sources-option-spec
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
       "Inspect and validate Isaac configuration\n\n"
       "Subcommands:\n"
       "  set <path> <value> Set a value at a dotted path\n"
       "  set <path> -       Read EDN value from stdin\n"
        "  schema [path]      Print config schema\n"
        "  schema --all       Expand every section\n"
        "  sources            List contributing config files\n"
       "  unset <path>       Remove a value at a dotted path\n"
        "  validate           Validate config\n"
        "  get <path>         Get a value by dotted key path\n\n"
       "Options:\n"
       "      --raw          Print pre-substitution config\n"
       "      --reveal       Reveal secrets after confirmation\n"
       "  -h, --help         Show help"))

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

(defn- validate-overlay! [opts relpath]
  (let [result (loader/load-config-result {:home             (home-dir opts)
                                           :overlay-content  (slurp *in*)
                                           :overlay-path     relpath})]
    (print-errors! (:errors result) "error")
    (print-warnings! (:warnings result))
    (if (seq (:errors result))
      1
      (do
        (println "OK")
        0))))

(defn- validate-config! [opts]
  (let [result (load-result opts)]
    (print-errors! (:errors result) "error")
    (print-warnings! (:warnings result))
    (if (seq (:errors result))
      1
      (do
        (println "OK")
        0))))

(defn- get-value! [opts path reveal?]
  (let [{:keys [config errors warnings]} (if reveal?
                                           (printable-config opts true)
                                           (printable-config opts false))]
    (cond
      (seq errors)
      (do
        (print-errors! errors "error")
        (print-warnings! warnings)
        1)

      (and reveal? (not (reveal-confirmed?)))
      (do
        (print-reveal-refused!)
        1)

      (or (str/includes? path "._")
          (str/includes? path "[_]")
          (str/includes? path ".*")
          (str/includes? path "[*]"))
      (do
        (binding [*out* *err*]
          (println "wildcards are not supported in config get"))
        1)

      :else
      (let [queryable (queryable-config config)
            value     (path/data-at queryable path)]
        (if (value-present? value)
          (do
            (print-warnings! warnings)
            (print-edn! (present-identifiers value))
            0)
          (do
            (binding [*out* *err*]
              (println (str "not found: " path)))
            1))))))

(defn- stdout-tty? []
  (and (some? (System/console))
       (not (instance? java.io.StringWriter *out*))))

(defn- schema-title [path-str spec]
  (let [spec-name (some-> (:name spec) name)]
    (if (or (nil? path-str) (str/blank? path-str))
      (str spec-name " config schema")
      (let [last-seg (last (str/split path-str #"\."))]
        (if (and spec-name (not= spec-name last-seg))
          (str path-str " (" spec-name " entity) config schema")
          (str path-str " config schema"))))))

(defn- schema-guidance []
  (str "\nTry:\n"
       "  isaac config schema crew\n"
       "  isaac config schema providers._\n"
       "  isaac config schema crew._.model"))

(defn- print-schema! [path-str expand-all?]
  (if-let [spec (config-schema/schema-for-path path-str)]
    (let [root?  (or (nil? path-str) (str/blank? path-str))
          deep?  (or expand-all? (not root?))
          title  (schema-title path-str spec)
          output (schema-term/spec->term spec {:color? (stdout-tty?) :deep? deep? :title title :width 80})]
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
               :wildcard          "wildcards and indexes are not supported for set/unset"
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

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Entry Point -----

(defn run [opts args]
  (let [subcmd   (first args)
        sub-args (rest args)]
    (cond
      (nil? subcmd)
      (print-config! opts)

      (or (= "--help" subcmd) (= "-h" subcmd))
      (do
        (println (config-help))
        0)

      (= "validate" subcmd)
      (let [{:keys [arguments errors options]} (parse-option-map sub-args validate-option-spec)]
        (cond
          (:help options) (do (println (config-help)) 0)
          (seq errors)    (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          (:as options)   (if (= "-" (first arguments))
                            (validate-overlay! opts (:as options))
                            (do (binding [*out* *err*] (println "validate --as requires '-' stdin source")) 1))
          :else           (validate-config! opts)))

      (= "sources" subcmd)
      (let [{:keys [errors options]} (parse-option-map sub-args sources-option-spec)]
        (cond
          (:help options) (do (println (config-help)) 0)
          (seq errors)    (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          :else           (print-sources! opts)))

      (= "get" subcmd)
      (let [{:keys [arguments errors options]} (parse-option-map sub-args get-option-spec)]
        (cond
          (:help options)      (do (println (config-help)) 0)
          (seq errors)         (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          (str/blank? (first arguments)) (do (binding [*out* *err*] (println "missing path")) 1)
          :else                (get-value! opts (first arguments) (:reveal options))))

      (= "schema" subcmd)
      (let [{:keys [arguments errors options]} (parse-option-map sub-args schema-option-spec)]
        (cond
          (:help options) (do (println (config-help)) 0)
          (seq errors)    (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          :else           (print-schema! (first arguments) (:all options))))

      (= "set" subcmd)
      (cond
        (some #{(first sub-args)} ["-h" "--help"]) (do (println (config-help)) 0)
        (str/blank? (first sub-args)) (do (binding [*out* *err*] (println "missing path")) 1)
        (nil? (second sub-args)) (do (binding [*out* *err*] (println "missing value")) 1)
        :else (mutate-config! opts :set (first sub-args) (second sub-args)))

      (= "unset" subcmd)
      (cond
        (some #{(first sub-args)} ["-h" "--help"]) (do (println (config-help)) 0)
        (str/blank? (first sub-args)) (do (binding [*out* *err*] (println "missing path")) 1)
        :else (mutate-config! opts :unset (first sub-args) nil))

      :else
      (let [{:keys [arguments errors options]} (parse-option-map args option-spec :in-order true)]
        (cond
          (seq errors)      (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          (:help options)   (do (println (config-help)) 0)
          (:raw options)    (print-raw-config! opts)
          (:reveal options) (print-revealed-config! opts)
          (seq arguments)   (do (binding [*out* *err*] (println (str "Unknown config subcommand: " (first arguments)))) 1)
          :else             (print-config! opts))))))

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
