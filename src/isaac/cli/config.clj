(ns isaac.cli.config
  (:require
    [c3kit.apron.schema.path :as path]
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [clojure.walk :as walk]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.fs :as fs]
    [isaac.config.loader :as loader]
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

(declare path-template-value)

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

      :else
      (let [queryable (queryable-config config)
            value     (if (str/includes? path "._")
                        (path-template-value queryable path)
                        (path/data-at queryable path))]
        (if (value-present? value)
        (do
          (print-warnings! warnings)
          (print-edn! (present-identifiers value))
          0)
        (do
          (binding [*out* *err*]
            (println (str "not found: " path)))
          1))))))

(defn- print-schema! [path-str expand-all?]
  (if-let [spec (config-schema/schema-for-path path-str)]
    (do
      (println (schema-term/spec->term spec {:color? false :deep? expand-all? :path path-str :width 80}))
      0)
    (do
      (binding [*out* *err*]
        (println (str "Path not found in config schema: " path-str)))
      1)))

(def ^:private entity-sections #{:crew :models :providers})

(defn- config-root-path [opts]
  (str (home-dir opts) "/.isaac/config"))

(defn- config-path [opts relative]
  (str (config-root-path opts) "/" relative))

(defn- entity-relative-path [kind id]
  (str (name kind) "/" id ".edn"))

(defn- soul-relative-path [id]
  (str "crew/" id ".md"))

(defn- read-edn-path [path]
  (when (fs/exists? path)
    (edn/read-string (fs/slurp path))))

(defn- copy-tree! [source-fs target-fs path]
  (binding [fs/*fs* source-fs]
    (when (fs/exists? path)
      (if (fs/file? path)
        (let [content (fs/slurp path)
              parent  (fs/parent path)]
          (binding [fs/*fs* target-fs]
            (when parent
              (fs/mkdirs parent))
            (fs/spit path content)))
        (do
          (binding [fs/*fs* target-fs]
            (fs/mkdirs path))
          (doseq [child (or (fs/children path) [])]
            (copy-tree! source-fs target-fs (str path "/" child))))))))

(defn- candidate-keys [segment]
  (cond
    (keyword? segment) [segment (name segment)]
    (string? segment)  [(keyword segment) segment]
    :else              [segment]))

(defn- existing-key [m segment]
  (some #(when (contains? m %) %) (candidate-keys segment)))

(defn- new-key [segment]
  (cond
    (keyword? segment) segment
    (string? segment)  (keyword segment)
    :else              segment))

(defn- path-present? [data segments]
  (if (empty? segments)
    true
    (and (map? data)
         (when-let [k (existing-key data (first segments))]
           (path-present? (get data k) (rest segments))))))

(defn- value-at-path [data segments]
  (if (empty? segments)
    data
    (when (map? data)
      (when-let [k (existing-key data (first segments))]
        (value-at-path (get data k) (rest segments))))))

(defn- path-template-value [data path-str]
  (let [segments (path/parse path-str)]
    (letfn [(step [current remaining]
              (if (empty? remaining)
                current
                (let [[kind segment] (first remaining)
                      more           (rest remaining)]
                  (cond
                    (nil? current)
                    nil

                    (and (= :key kind) (= :_ segment) (map? current))
                    (let [result (reduce-kv (fn [acc k v]
                                              (if-some [child (step v more)]
                                                (assoc acc k child)
                                                acc))
                                            {}
                                            current)]
                      (when (seq result) result))

                    (map? current)
                    (when-let [k (existing-key current segment)]
                      (step (get current k) more))

                    (and (vector? current) (= :index kind))
                    (when-let [item (nth current segment nil)]
                      (step item more))

                    :else
                    nil))))]
      (step data segments))))

(defn- assoc-path [data segments value]
  (if (empty? segments)
    value
    (let [data  (or data {})
          seg   (first segments)
          k     (or (existing-key data seg) (new-key seg))
          child (get data k)]
      (assoc data k (assoc-path child (rest segments) value)))))

(defn- dissoc-path [data segments]
  (if (and (map? data) (seq segments))
    (if-let [k (existing-key data (first segments))]
      (if-let [more (next segments)]
        (let [child   (dissoc-path (get data k) more)
              updated (if (nil? child) (dissoc data k) (assoc data k child))]
          (when (seq updated) updated))
        (let [updated (dissoc data k)]
          (when (seq updated) updated)))
      data)
    data))

(defn- parse-config-path [path-str]
  (let [segments (path/parse path-str)]
    (cond
      (empty? segments)
      {:error "missing path"}

      (some #(#{:wildcard :index} (first %)) segments)
      {:error "wildcards and indexes are not supported for set/unset"}

      :else
      (let [segments    (mapv second segments)
            root-key    (first segments)
            entity?     (contains? entity-sections root-key)
            field-path  (if entity? (subvec segments 2) (subvec segments 1))]
        (cond
          (and entity? (< (count segments) 2))
          {:error "missing entity id"}

          :else
          {:entity-id     (when entity? (config-schema/->id (second segments)))
           :entity?       entity?
           :field-path    field-path
           :path          path-str
           :root-key      root-key
           :root-path     (if entity? [(first segments) (second segments)] [(first segments)])
           :segments      segments
           :soul?         (and entity? (= :crew root-key) (= [:soul] field-path))
           :whole-entity? (and entity? (= 2 (count segments)))})))))

(defn- config-state [opts parsed]
  (let [root-path         (config-path opts "isaac.edn")
        root-data         (or (read-edn-path root-path) {})
        entity-relative   (when (:entity? parsed) (entity-relative-path (:root-key parsed) (:entity-id parsed)))
        entity-path       (when entity-relative (config-path opts entity-relative))
        entity-data       (or (some-> entity-path read-edn-path) {})
        soul-relative     (when (:soul? parsed) (soul-relative-path (:entity-id parsed)))
        soul-path         (when soul-relative (config-path opts soul-relative))]
    {:entity-data           entity-data
     :entity-exists?        (boolean (and entity-path (fs/exists? entity-path)))
     :entity-path           entity-path
     :entity-relative       entity-relative
     :entity-root-exists?   (and (:entity? parsed) (path-present? root-data (:root-path parsed)))
     :inline-entity-soul?   (and (:soul? parsed) (path-present? entity-data [:soul]))
     :inline-root-soul?     (and (:soul? parsed) (path-present? root-data (:segments parsed)))
     :md-exists?            (boolean (and soul-path (fs/exists? soul-path)))
     :prefer-entity-files?  (true? (value-at-path root-data [:prefer-entity-files]))
     :root-data             root-data
     :root-path-exists?     (path-present? root-data (:segments parsed))
     :root-path             root-path
     :soul-path             soul-path
     :soul-relative         soul-relative
     :target-spec           (config-schema/schema-for-data-path (:path parsed))}))

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

(defn- update-edn-file [plan relative data]
  (if (nil? data)
    (-> plan
        (update :writes dissoc relative)
        (update :deletes conj relative))
    (-> plan
        (update :deletes disj relative)
        (assoc-in [:writes relative] (pr-str data)))))

(defn- update-text-file [plan relative content]
  (-> plan
      (update :deletes disj relative)
      (assoc-in [:writes relative] content)))

(defn- choose-set-location [parsed state]
  (cond
    (and (:soul? parsed) (:md-exists? state)) :md
    (and (:soul? parsed) (:inline-root-soul? state)) :root
    (and (:soul? parsed) (:inline-entity-soul? state)) :entity
    (and (:entity? parsed) (:entity-root-exists? state)) :root
    (and (:entity? parsed) (:entity-exists? state)) :entity
    (and (:entity? parsed) (:prefer-entity-files? state)) :entity
    :else :root))

(defn- choose-unset-location [parsed state]
  (cond
    (and (:soul? parsed) (:md-exists? state)) :md
    (:root-path-exists? state) :root
    (and (:entity? parsed)
         (or (and (:whole-entity? parsed) (:entity-exists? state))
             (path-present? (:entity-data state) (:field-path parsed)))) :entity
    :else nil))

(defn- use-soul-markdown? [parsed state location value]
  (and (:soul? parsed)
       (= :entity location)
       (not (:md-exists? state))
       (not (:inline-entity-soul? state))
       (string? value)
       (> (count value) 64)))

(defn- set-plan [parsed state value]
  (let [location (choose-set-location parsed state)]
    (cond
      (or (= :md location) (use-soul-markdown? parsed state location value))
      (let [entity-data' (when (:entity-exists? state)
                           (dissoc-path (:entity-data state) [:soul]))]
        (cond-> {:deletes #{} :file (:soul-relative state) :writes {}}
          true (update-text-file (:soul-relative state) value)
          (:entity-exists? state) (update-edn-file (:entity-relative state) entity-data')))

      (= :entity location)
      (let [entity-data' (if (:whole-entity? parsed)
                           value
                           (assoc-path (:entity-data state) (:field-path parsed) value))]
        (-> {:deletes #{} :file (:entity-relative state) :writes {}}
            (update-edn-file (:entity-relative state) entity-data')))

      :else
      (let [root-data' (assoc-path (:root-data state) (:segments parsed) value)]
        (-> {:deletes #{} :file "isaac.edn" :writes {}}
            (update-edn-file "isaac.edn" root-data'))))))

(defn- unset-plan [parsed state]
  (when-let [location (choose-unset-location parsed state)]
    (case location
      :md
      {:deletes #{(:soul-relative state)} :file (:soul-relative state) :writes {}}

      :entity
      (let [entity-data' (if (:whole-entity? parsed)
                           nil
                           (dissoc-path (:entity-data state) (:field-path parsed)))]
        (-> {:deletes #{} :file (:entity-relative state) :writes {}}
            (update-edn-file (:entity-relative state) entity-data')))

      :root
      (let [root-data' (dissoc-path (:root-data state) (:segments parsed))]
        (-> {:deletes #{} :file "isaac.edn" :writes {}}
            (update-edn-file "isaac.edn" root-data'))))))

(defn- apply-plan! [opts plan]
  (doseq [relative (:deletes plan)]
    (let [path (config-path opts relative)]
      (when (fs/exists? path)
        (fs/delete path))))
  (doseq [[relative content] (:writes plan)]
    (let [path   (config-path opts relative)
          parent (fs/parent path)]
      (when parent
        (fs/mkdirs parent))
      (fs/spit path content))))

(defn- validate-plan [opts plan]
  (let [source-fs (or fs/*fs* (fs/mem-fs))
        stage-fs  (fs/mem-fs)
        root      (config-root-path opts)]
    (copy-tree! source-fs stage-fs root)
    (binding [fs/*fs* stage-fs]
      (apply-plan! opts plan)
      (load-result opts))))

(defn- format-errors [errors]
  (str/join "; " (map (fn [{:keys [key value]}] (str key " - " value)) errors)))

(defn- mutation-allowed? [result]
  (not (seq (:errors result))))

(defn- log-mutation! [level event file path-str & kvs]
  (apply log/log* level event file 0 :path path-str kvs))

(defn- mutate-config! [opts operation path-str raw-value]
  (let [{:keys [error] :as parsed} (parse-config-path path-str)]
    (cond
      error
      (do
        (binding [*out* *err*]
          (println error))
        1)

      :else
      (let [current (load-result opts)]
        (if (and (not (:missing-config? current)) (seq (:errors current)))
          (do
            (print-errors! (:errors current) "error")
            (print-warnings! (:warnings current))
            1)
          (let [state        (config-state opts parsed)
                extra-warnings (if (and (= :set operation) (nil? (:target-spec state)))
                                 [{:key path-str :value "unknown key"}]
                                 [])
                value-result (when (= :set operation)
                               (if (= "-" raw-value)
                                 (read-stdin-value)
                                 {:value (parse-set-value (:target-spec state) raw-value)}))
                plan         (case operation
                               :set (if-let [value (:value value-result)]
                                      (set-plan parsed state value)
                                      nil)
                               :unset (unset-plan parsed state))]
            (cond
              (:error value-result)
              (do
                (binding [*out* *err*]
                  (println (:error value-result)))
                (log-mutation! :error :config/set-failed "config" path-str :error (:error value-result))
                1)

              (nil? plan)
              (do
                (binding [*out* *err*]
                  (println (str "not found: " path-str)))
                1)

              :else
              (let [result (validate-plan opts plan)]
                (print-warnings! (concat extra-warnings (:warnings result)))
                (if (mutation-allowed? result)
                  (do
                    (apply-plan! opts plan)
                    (case operation
                      :set   (log-mutation! :info :config/set (:file plan) path-str :value (:value value-result))
                      :unset (log-mutation! :info :config/unset (:file plan) path-str))
                    0)
                  (do
                    (print-errors! (:errors result) "error")
                    (when (= :set operation)
                      (log-mutation! :error :config/set-failed "config" path-str :error (format-errors (:errors result))))
                    1))))))))))

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
