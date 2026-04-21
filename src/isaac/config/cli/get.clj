(ns isaac.config.cli.get
  "isaac config get — read the resolved config (or a subtree) by config path."
  (:require
    [c3kit.apron.schema.path :as path]
    [isaac.config.cli.common :as common]))

(def option-spec
  [[nil  "--raw"    "Print pre-substitution config"]
   [nil  "--reveal" "Reveal secrets after confirmation"]
   ["-h" "--help"   "Show help"]])

(defn help []
  (str "Usage: isaac config get [config-path] [options]\n\n"
       "Read from the resolved config. With no path, prints the whole config.\n"
       "With a config path, prints the subtree at that path.\n\n"
       "Options:\n"
       "      --raw     Print pre-substitution config (raw ${VAR} tokens intact)\n"
       "      --reveal  Reveal ${VAR} secrets after confirmation (type REVEAL on stdin)\n"
       "  -h, --help    Show help\n\n"
       "Examples:\n"
       "  isaac config get\n"
       "  isaac config get --raw\n"
       "  isaac config get crew.marvin.soul\n"
       "  isaac config get providers.anthropic.api-key --reveal"))

(defn- select [config path-str]
  (if (or (nil? path-str) (clojure.string/blank? path-str))
    config
    (let [value (path/data-at (common/queryable-config config) path-str)]
      (when (common/value-present? value) value))))

(defn- load-result [opts raw? reveal?]
  (cond
    raw?    (common/load-raw-result opts)
    :else   (common/printable-config opts reveal?)))

(defn- get-value! [opts path-str {:keys [raw reveal]}]
  (let [{:keys [config errors missing-config?] :as result} (load-result opts raw reveal)]
    (cond
      missing-config?
      (do (common/print-errors! errors "error") 1)

      (and reveal (not (common/reveal-confirmed?)))
      (do (common/print-reveal-refused!) 1)

      :else
      (let [value (select config path-str)]
        (if (common/value-present? value)
          (do (common/print-edn! (common/present-identifiers value)) 0)
          (do
            (binding [*out* *err*]
              (println (str "not found: " path-str)))
            1))))))

(defn run [opts arguments options]
  (get-value! opts (common/normalize-path (first arguments)) options))

(def subcommand
  {:option-spec option-spec
   :runner      run
   :help-text   help})
