(ns isaac.logs.cli
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.cli.common :as cli-common]
    [isaac.fs :as fs]
    [isaac.log-viewer :as viewer]
    [isaac.logger :as log]
    [isaac.system :as system]))

(def ^:private default-limit 20)

(def option-spec
  [[nil  "--file PATH" "Log file path (overrides configured path)"]
   ["-f" "--follow" "Follow the file for new entries (default: read and exit)"]
   ["-n" "--limit N" (str "Show last N entries; 0 = all (default: " default-limit ")")
    :default default-limit
    :parse-fn #(Long/parseLong %)]
   [nil  "--no-color" "Disable color output"]
   [nil  "--zebra" "Enable alternating row background"]
   [nil  "--plain" "Raw passthrough — no parsing, color, or zebra"]
   ["-h" "--help" "Show help"]])

(defn- resolve-path [file state-dir]
  (cond
    (nil? file)                         nil
    (str/starts-with? file "/")         file
    (and state-dir (seq state-dir))     (str state-dir "/" file)
    :else                               file))

(defn- config-log-path [home fs*]
  (when home
    (let [config-file (str home "/.isaac/config/isaac.edn")]
      (when (fs/exists? fs* config-file)
        (try
          (get-in (edn/read-string (fs/slurp fs* config-file)) [:log :output])
          (catch Exception _ nil))))))

(defn run [{:keys [file follow limit no-color zebra plain state-dir home]}]
  (let [log-path (or (resolve-path file state-dir)
                     (resolve-path (config-log-path home (system/get :fs)) state-dir)
                     (log/log-file))]
    (viewer/tail! log-path
                  {:color?  (not no-color)
                   :zebra?  (boolean zebra)
                   :follow? (boolean follow)
                   :plain?  (boolean plain)
                   :limit   limit})))

(defn run-fn [opts]
  (cli-common/standard-run-fn "logs"
                               #(tools-cli/parse-opts % option-spec)
                               run
                               opts))

(registry/register!
  {:name        "logs"
   :usage       "logs [options]"
   :desc        "Tail and colorize the Isaac log file"
   :option-spec option-spec
   :run-fn      run-fn})
