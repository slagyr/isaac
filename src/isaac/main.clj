;; mutation-tested: 2026-05-06
(ns isaac.main
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.cli :as registry]
    [isaac.config.loader :as config]
    [isaac.config.paths :as paths]
    [isaac.fs :as fs]
    [isaac.home :as home]
    [isaac.module.loader :as module-loader]
    [isaac.session.store :as store]
    [isaac.system :as system]
    [isaac.version :as version]
    isaac.llm.auth.cli
    isaac.config.cli.command
    isaac.crew.cli
    isaac.bridge.prompt-cli
    isaac.logs.cli
    isaac.server.cli
    isaac.service.cli
    isaac.session.cli))

(def ^:dynamic *extra-opts* nil)

(defn- startup-fs [extra-opts]
  (or (:fs extra-opts)
      fs/*fs*
      (:fs (system/current))))

(defn- substitute-env [x]
  (cond
    (string? x) (str/replace x #"\$\{([^}]+)\}"
                   (fn [[_ var]] (or (config/env var) (str "${" var "}"))))
    (map? x)    (into {} (map (fn [[k v]] [k (substitute-env v)]) x))
    (coll? x)   (mapv substitute-env x)
    :else        x))

(defn- register-module-cli-commands! [home fs*]
  (registry/clear-module-commands!)
  (when home
    (let [config-file (paths/root-config-file home)]
      (when (fs/exists?- fs* config-file)
        (try
          (system/with-nested-system {:fs fs*}
            (let [config  (substitute-env (edn/read-string (fs/slurp- fs* config-file)))
                  context {:cwd (System/getProperty "user.dir")}
                  {:keys [index]} (module-loader/discover! config context)]
              (doseq [[_mod-id entry] index
                      [cli-id cli-ext] (get-in entry [:manifest :cli])]
                (module-loader/register-cli-extension! cli-id cli-ext))))
          (catch Exception _
            nil))))))

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
         fs*           (startup-fs extra-opts)
         resolved-home (home/resolve-home home (or (:home extra-opts) (:state-dir extra-opts)) fs*)]
    (register-module-cli-commands! resolved-home fs*)
    (cond
      (or (nil? cmd) (str/blank? cmd) (= "--help" cmd) (= "-h" cmd))
      (do (println (usage)) 0)

      (or (= "--version" cmd) (= "-V" cmd) (= "version" cmd))
      (do (println (version/version-string)) 0)

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
            (system/with-nested-system {:fs fs*}
              (system/init! {:fs fs*})
              (let [state-dir (str resolved-home "/.isaac")]
                (system/register! :state-dir state-dir)
                (store/register! (or (config/snapshot) {}) state-dir))
              (or ((:run-fn command) (merge extra-opts {:display-home (or home resolved-home)
                                                        :home         resolved-home
                                                        :_raw-args    (vec opts)})) 0)))
          (do (println (str "Unknown command: " cmd))
              (println (usage))
              1)))))

(defn -main [& args]
  (let [exit-code (run args)]
    (when (pos? exit-code)
      (System/exit exit-code))))
