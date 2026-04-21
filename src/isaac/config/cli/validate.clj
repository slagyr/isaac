(ns isaac.config.cli.validate
  "isaac config validate — validate the config composition."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.config.cli.common :as common]
    [isaac.config.loader :as loader]))

(def option-spec
  [[nil  "--as PATH" "Overlay stdin at a config path before validating"]
   ["-h" "--help"   "Show help"]])

(defn help []
  (str "Usage: isaac config validate [options] [-]\n\n"
       "Validate the config composition\n\n"
       "Options:\n"
       "      --as <config-path>  Overlay stdin EDN at the given config path before validating\n"
       "  -h, --help              Show help\n\n"
       "Arguments:\n"
       "  -                       Read EDN to validate from stdin (isolated when no --as)"))

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
  (common/print-errors! errors "error")
  (common/print-warnings! warnings)
  (if (seq errors)
    1
    (do
      (println "OK - config is valid")
      0)))

(defn- validate-stdin! [opts]
  (report-validation!
    (loader/load-config-result {:home               (common/home-dir opts)
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
        (loader/load-config-result {:home              (common/home-dir opts)
                                    :data-path-overlay {:path  (parse-data-path data-path-str)
                                                        :value (:value stdin-value)}})))))

(defn- validate-config! [opts]
  (report-validation! (common/load-result opts)))

(defn- file-path-style? [path-str]
  (and path-str
       (not (str/starts-with? path-str "/"))
       (str/includes? path-str "/")))

(defn run [opts arguments options]
  (let [as-value (:as options)
        stdin?   (= "-" (first arguments))]
    (cond
      (file-path-style? as-value)
      (common/print-cli-error! (str "validate --as expected a config path like foo.bar, got file path: " as-value))

      as-value
      (if stdin?
        (validate-overlay-data! opts (common/normalize-path as-value))
        (common/print-cli-error! "validate --as requires '-' stdin source"))

      stdin?
      (validate-stdin! opts)

      :else
      (validate-config! opts))))

(def subcommand
  {:option-spec option-spec
   :runner      run
   :help-text   help})
