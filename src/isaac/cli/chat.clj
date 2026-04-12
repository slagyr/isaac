;; mutation-tested: 2026-04-08
(ns isaac.cli.chat
  (:require
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.chat.loop :as chat-loop]
    [isaac.cli.chat.toad :as toad]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]
    [isaac.util.shell :as shell]))

(def option-spec
  [["-a" "--agent NAME"  "Use a named agent (default: main)"]
   ["-m" "--model ALIAS" "Override the agent's default model"]
   ["-r" "--resume"      "Resume the most recent session"]
   ["-s" "--session KEY" "Resume a specific session by key"]
   ["-t" "--toad"        "Launch Toad TUI via ACP"]
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
  (if (:toad opts)
    (run-toad! opts)
    (let [ctx (chat-loop/prepare opts)]
      (builtin/register-all! tool-registry/register!)
      (println (str "Isaac — agent:" (:agent ctx) " model:" (:model ctx)))
      (println (str "Session: " (:session-key ctx)))
      (let [cfg             (config/load-config)
            provider-config (or (config/resolve-provider cfg (:provider ctx))
                                {:baseUrl (:base-url ctx)})]
        (chat-loop/chat-loop (:state-dir ctx) (:session-key ctx)
                             {:soul            (:soul ctx)
                              :model           (:model ctx)
                              :provider        (:provider ctx)
                              :provider-config provider-config
                              :context-window  (:context-window ctx)})))))

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
   :desc    "Start an interactive chat session"
   :option-spec option-spec
   :run-fn  run-fn})
