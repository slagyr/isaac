;; mutation-tested: 2026-04-08
(ns isaac.cli.chat
  (:require
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.chat.toad :as toad]
    [isaac.cli.registry :as registry]
    [isaac.util.shell :as shell]))

(def option-spec
  [["-a" "--agent NAME"  "Use a named agent (default: main)"]
   ["-m" "--model ALIAS" "Override the agent's default model"]
   ["-R" "--remote URL"  "Proxy ACP over a remote WebSocket endpoint"]
   ["-r" "--resume"      "Resume the most recent session"]
   ["-s" "--session KEY" "Resume a specific session by key"]
   ["-T" "--token TOKEN" "Bearer token for remote ACP authentication"]
   ["-d" "--dry-run"     "Print the Toad launch command without spawning"]
   ["-h" "--help"        "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options
                   (remove (comp nil? val))
                   (into {}))
     :errors  errors}))

(defn- run-toad! [opts]
  (if-not (shell/cmd-available? "toad")
    (do (println "Toad not found. Install it at batrachian.ai/install")
        1)
    (if (:dry-run opts)
      (do (println (toad/format-toad-command opts))
          0)
      (toad/spawn-toad! opts))))

(defn run [opts]
  (run-toad! opts))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "chat")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name    "chat"
   :usage   "chat [options]"
   :desc    "Launch Toad chat UI"
   :option-spec option-spec
   :run-fn  run-fn})
