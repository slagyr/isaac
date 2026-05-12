(ns isaac.logs.cli
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.fs :as fs]
    [isaac.log-viewer :as viewer]
    [isaac.logger :as log]))

(def ^:private default-limit 20)

(def option-spec
  [[nil  "--file PATH" "Log file path (overrides configured path)"]
   ["-f" "--follow" "Follow the file for new entries (default: read and exit)"]
   ["-n" "--limit N" (str "Show last N entries; 0 = all (default: " default-limit ")")
    :default default-limit
    :parse-fn #(Long/parseLong %)]
   [nil  "--no-color" "Disable color output"]
   [nil  "--no-zebra" "Disable alternating row background"]
   [nil  "--plain" "Raw passthrough — no parsing, color, or zebra"]
   ["-h" "--help" "Show help"]])

(defn- resolve-path [file state-dir]
  (cond
    (nil? file)                         nil
    (str/starts-with? file "/")         file
    (and state-dir (seq state-dir))     (str state-dir "/" file)
    :else                               file))

(defn- config-log-path [home]
  (when home
    (let [config-file (str home "/.isaac/config/isaac.edn")]
      (when (fs/exists? config-file)
        (try
          (get-in (edn/read-string (fs/slurp config-file)) [:log :output])
          (catch Exception _ nil))))))

(defn run [{:keys [file follow limit no-color no-zebra plain state-dir home]}]
  (let [log-path (or (resolve-path file state-dir)
                     (resolve-path (config-log-path home) state-dir)
                     (log/log-file))]
    (viewer/tail! log-path
                  {:color?  (not no-color)
                   :zebra?  (not no-zebra)
                   :follow? (boolean follow)
                   :plain?  (boolean plain)
                   :limit   limit})))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (tools-cli/parse-opts (or _raw-args []) option-spec)]
    (cond
      (:help options)
      (do (println (registry/command-help (registry/get-command "logs"))) 0)

      (seq errors)
      (do (doseq [e errors] (println e)) 1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name        "logs"
   :usage       "logs [options]"
   :desc        "Tail and colorize the Isaac log file"
   :option-spec option-spec
   :run-fn      run-fn})
