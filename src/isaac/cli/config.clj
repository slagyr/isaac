(ns isaac.cli.config
  (:require
    [clojure.walk :as walk]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as loader]))

;; region ----- Helpers -----

(def ^:private option-spec
  [[nil  "--raw"     "Print pre-substitution config"]
   [nil  "--reveal"  "Reveal secrets after confirmation"]
   [nil  "--sources" "List contributing config files"]
   ["-h" "--help"    "Show help"]])

(def ^:private validate-option-spec
  [[nil  "--as RELPATH" "Overlay stdin as a config file path"]
   ["-h" "--help"      "Show help"]])

(def ^:private get-option-spec
  [[nil  "--reveal" "Reveal secrets after confirmation"]
   ["-h" "--help"   "Show help"]])

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

(defn- print-errors! [entries label]
  (binding [*out* *err*]
    (doseq [{:keys [key value]} entries]
      (println (str label ": " key " - " value)))))

(defn- print-warnings! [entries]
  (binding [*out* *err*]
    (doseq [{:keys [key value]} entries]
      (println (str "warning: :" key " - " value)))))

(defn- read-stdin []
  (str/trim (slurp *in*)))

(defn- reveal-confirmed? []
  (= "REVEAL" (read-stdin)))

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

(defn- get-path [data path]
  (reduce (fn [current segment]
            (cond
              (nil? current) nil
              (map? current) (or (get current (keyword segment))
                                 (get current segment))
              (vector? current) (nth current (parse-long segment) nil)
              :else nil))
          data
          (str/split path #"\.")))

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
       "  validate           Validate config\n"
       "  get <path>         Get a value by dotted key path\n\n"
       "Options:\n"
       "      --raw          Print pre-substitution config\n"
       "      --reveal       Reveal secrets after confirmation\n"
       "      --sources      List contributing config files\n"
       "  -h, --help         Show help"))

(defn- print-config! [opts]
  (let [{:keys [config errors warnings]} (printable-config opts false)]
    (print-errors! errors "error")
    (print-warnings! warnings)
    (if (seq errors)
      1
      (do
        (println (pr-str (present-identifiers config)))
        0))))

(defn- print-raw-config! [opts]
  (let [{:keys [config errors warnings]} (load-raw-result opts)]
    (print-errors! errors "error")
    (print-warnings! warnings)
    (if (seq errors)
      1
      (do
        (println (pr-str (present-identifiers config)))
        0))))

(defn- print-revealed-config! [opts]
  (if-not (reveal-confirmed?)
    (do
      (binding [*out* *err*]
        (println "type REVEAL to confirm"))
      1)
    (let [{:keys [config errors warnings]} (printable-config opts true)]
      (print-errors! errors "error")
      (print-warnings! warnings)
      (if (seq errors)
        1
        (do
          (println (pr-str (present-identifiers config)))
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
        (binding [*out* *err*]
          (println "type REVEAL to confirm"))
        1)

      :else
      (let [value (get-path config path)]
        (if (value-present? value)
        (do
          (print-warnings! warnings)
          (println (pr-str (present-identifiers value)))
          0)
        (do
          (binding [*out* *err*]
            (println (str "not found: " path)))
          1))))))

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

      (= "get" subcmd)
      (let [{:keys [arguments errors options]} (parse-option-map sub-args get-option-spec)]
        (cond
          (:help options)      (do (println (config-help)) 0)
          (seq errors)         (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          (str/blank? (first arguments)) (do (binding [*out* *err*] (println "missing path")) 1)
          :else                (get-value! opts (first arguments) (:reveal options))))

      :else
      (let [{:keys [arguments errors options]} (parse-option-map args option-spec :in-order true)]
        (cond
          (seq errors)      (do (binding [*out* *err*] (doseq [error errors] (println error))) 1)
          (:help options)   (do (println (config-help)) 0)
          (:sources options) (print-sources! opts)
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
