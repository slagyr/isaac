(ns isaac.service.cli
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.service.macos :as macos]
    [isaac.util.shell :as shell]))

(def ^:private install-options
  [[nil "--bb-bin PATH" "Path to bb binary (default: resolved via which)"]
   [nil "--isaac-dir PATH" "Path to Isaac repo root (default: current directory)"]
   ["-h" "--help" "Show help"]])

(def ^:private logs-options
  [["-f" "--follow" "Follow log output (tail -f)"]
   ["-h" "--help" "Show help"]])

(defn- find-bb [bb-bin-override]
  (if bb-bin-override
    bb-bin-override
    (let [result (shell/sh! "which" "bb")]
      (when (zero? (:exit result))
        (str/trim (:out result))))))

(defn- bb-edn-dir [isaac-dir-override]
  (or isaac-dir-override (System/getProperty "user.dir")))

(defn- unsupported-os [os]
  (binding [*out* *err*]
    (println (str "isaac service is not yet supported on " os)))
  1)

(defn- run-install [opts]
  (let [{:keys [options errors]} (tools-cli/parse-opts (or (:_raw-args opts) []) install-options)]
    (cond
      (:help options) (do (println "Usage: isaac service install [options]") 0)
      (seq errors)    (do (binding [*out* *err*] (doseq [e errors] (println e))) 1)
      :else
      (let [bb-bin (find-bb (:bb-bin options))]
        (if-not bb-bin
          (do
            (binding [*out* *err*]
              (println "could not locate bb on PATH")
              (println "pass --bb-bin <path> to specify it explicitly"))
            1)
          (let [bb-edn (bb-edn-dir (:isaac-dir options))]
            (macos/install! {:bb-bin bb-bin :bb-edn bb-edn})
            (println (str "Resolved bb: " bb-bin))
            (println (str "Service installed: com.slagyr.isaac"))
            0))))))

(defn- run-uninstall [opts]
  (macos/uninstall! opts)
  (println "Service uninstalled (or already uninstalled)")
  0)

(defn- run-start [opts]
  (macos/start! opts)
  (println "Service started")
  0)

(defn- run-stop [opts]
  (macos/stop! opts)
  (println "Service stopped")
  0)

(defn- run-restart [opts]
  (macos/restart! opts)
  (println "Service restarted")
  0)

(defn- run-status [_opts]
  (let [result (macos/status! {})]
    (if-not (:installed? result)
      (do (println "not installed") 1)
      (do
        (println (str "state: " (or (:state result) "unknown")))
        (when-let [pid (:pid result)]
          (println (str "pid:   " pid)))
        (when-let [exit (:last-exit result)]
          (println (str "last exit: " exit)))
        (if (= "running" (:state result)) 0 1)))))

(defn- run-logs [opts]
  (let [{:keys [options]} (tools-cli/parse-opts (or (:_raw-args opts) []) logs-options)
        result            (macos/logs! {:follow? (:follow options)})]
    (if-let [content (:content result)]
      (do (print content) 0)
      (do (binding [*out* *err*] (println "log file not found")) 1))))

(defn- dispatch [subcmd opts]
  (let [os (shell/os-name)]
    (if (= "Mac OS X" os)
      (case subcmd
        "install"   (run-install opts)
        "uninstall" (run-uninstall opts)
        "start"     (run-start opts)
        "stop"      (run-stop opts)
        "restart"   (run-restart opts)
        "status"    (run-status opts)
        "logs"      (run-logs opts)
        (do (binding [*out* *err*] (println (str "Unknown service subcommand: " subcmd))) 1))
      (unsupported-os os))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [args (or _raw-args [])]
    (if (empty? args)
      (do (println "Usage: isaac service <subcommand>") 0)
      (dispatch (first args) (assoc opts :_raw-args (vec (rest args)))))))

(registry/register!
  {:name        "service"
   :usage       "service <subcommand> [options]"
   :desc        "Manage Isaac as a background service"
   :run-fn      run-fn})
