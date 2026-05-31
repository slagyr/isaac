;; mutation-tested: 2026-05-06
(ns isaac.main
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.cli :as registry]
    [isaac.config.api :as config]
    [isaac.config.paths :as paths]
    [isaac.fs :as fs]
    [isaac.module.loader :as module-loader]
    [isaac.nexus :as nexus]
    [isaac.root :as root]
    [isaac.version :as version]
    isaac.llm.auth.cli
    isaac.config.cli.command
     isaac.crew.cli
     isaac.hail.cli
     isaac.bridge.prompt-cli
     isaac.logs.cli
    isaac.server.cli
    isaac.service.cli
    isaac.session.cli))

(def ^:dynamic *extra-opts* nil)

(defn- startup-fs [extra-opts]
  (or (fs/instance extra-opts) (fs/real-fs)))

(defn- substitute-env [x]
  (cond
    (string? x) (str/replace x #"\$\{([^}]+)\}"
                   (fn [[_ var]] (or (config/env var) (str "${" var "}"))))
    (map? x)    (into {} (map (fn [[k v]] [k (substitute-env v)]) x))
    (coll? x)   (mapv substitute-env x)
    :else        x))

(defn- register-module-cli-commands! [root fs*]
  (registry/clear-module-commands!)
  (when root
    (let [config-file (paths/root-config-file root)]
      (when (fs/exists? fs* config-file)
        (try
          (nexus/-with-nested-nexus {:fs fs*}
            (let [config  (substitute-env (edn/read-string (fs/slurp fs* config-file)))
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
         "  --root <dir>    Override Isaac's root directory (default: ~/.isaac)\n"
         "                  May also be set via ISAAC_ROOT, ~/.config/isaac.edn, or ~/.isaac.edn\n"
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
  (let [{after-root :args :keys [root]} (root/extract-root-flag args)
         {after-home :args :keys [home]} (root/extract-home-flag after-root)
         args (resolve-alias after-home)
         cmd  (first args)
         opts (rest args)
         extra-opts    (or *extra-opts* {})
         fs*           (startup-fs extra-opts)
         resolved-root (root/resolve-root root home (:state-dir extra-opts) fs*)]
    (register-module-cli-commands! resolved-root fs*)
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
         (binding [root/*root* resolved-root]
            (nexus/-with-nested-nexus {:fs fs*}
              (nexus/init! {:fs fs*})
              ;; :root is the new public name for the data directory; :state-dir is the
              ;; legacy internal key kept as a synonym until follow-up cleanup (see
              ;; isaac-root bean PR 2/3). Every consumer can read either.
              (or ((:run-fn command) (merge extra-opts {:display-root (or root home resolved-root)
                                                        :root         resolved-root
                                                        :state-dir    resolved-root
                                                        :_raw-args    (vec opts)})) 0)))
          (do (println (str "Unknown command: " cmd))
              (println (usage))
              1)))))

(defn -main [& args]
  (let [exit-code (run args)]
    (when (pos? exit-code)
      (System/exit exit-code))))
