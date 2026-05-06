(ns isaac.main
  (:require
    [clojure.string :as str]
    [isaac.cli :as registry]
    [isaac.home :as home]
    isaac.acp.cli
    isaac.auth.cli
    isaac.bridge.chat-cli
    isaac.config.cli.command
    isaac.crew.cli
    isaac.bridge.prompt-cli
    isaac.server.cli
    isaac.session.cli))

(def ^:dynamic *extra-opts* nil)

(defn- usage []
  (let [cmds (registry/all-commands)
        max-len (if (seq cmds) (apply max (map #(count (:name %)) cmds)) 0)]
    (str "Usage: isaac [options] <command> [args]\n\n"
         "Global Options:\n"
         "  --home <dir>    Override Isaac's home directory (default: ~/.isaac)\n"
         "                  May also be set via ~/.config/isaac.edn or ~/.isaac.edn\n"
         "  --help, -h      Show this message\n\n"
         "Commands:\n"
         (str/join "\n" (map (fn [cmd]
                               (str "  " (:name cmd)
                                    (apply str (repeat (- (+ max-len 4) (count (:name cmd))) " "))
                                    (:desc cmd)))
                             cmds)))))

(defn- resolve-alias
  "Resolve command aliases. 'models auth ...' → 'auth ...', 'gateway ...' → 'server ...'"
  [args]
  (cond
    (and (= "models" (first args)) (= "auth" (second args)))
    (rest args)

    (= "gateway" (first args))
    (vec (cons "server" (rest args)))

    :else args))

(defn run
  "Run the CLI. Returns exit code."
  [args]
  (let [{:keys [args home]} (home/extract-home-flag args)
        args (resolve-alias args)
        cmd  (first args)
        opts (rest args)
        extra-opts    (or *extra-opts* {})
        resolved-home (home/resolve-home home (or (:home extra-opts) (:state-dir extra-opts)))]
    (cond
      (or (nil? cmd) (str/blank? cmd) (= "--help" cmd) (= "-h" cmd))
      (do (println (usage)) 0)

      (= "help" cmd)
      (if-let [target (first opts)]
        (if-let [command (registry/get-command target)]
          (do (println (registry/command-help command)) 0)
          (do (println (str "Unknown command: " target)) 1))
        (do (println (usage)) 0))

       :else
       (if-let [command (registry/get-command cmd)]
         (binding [home/*resolved-home* resolved-home
                   home/*state-dir*     (str resolved-home "/.isaac")]
          (or ((:run-fn command) (merge extra-opts {:display-home (or home resolved-home)
                                                    :home         resolved-home
                                                    :_raw-args    (vec opts)})) 0))
         (do (println (str "Unknown command: " cmd))
             (println (usage))
             1)))))

(defn -main [& args]
  (let [exit-code (run args)]
    (when (pos? exit-code)
      (System/exit exit-code))))
