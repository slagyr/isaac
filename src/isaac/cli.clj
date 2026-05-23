;; mutation-tested: 2026-05-06
(ns isaac.cli
  (:require
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.config.paths :as paths]
    [isaac.fs :as fs]
    [isaac.nexus :as nexus]))

;; region ----- Command Registry -----

(defonce ^:private commands (atom {}))

(defn register!
  "Register a CLI command.
   Options:
     :name    - command name (string)
     :usage   - usage line (e.g. \"isaac chat [options]\")
     :desc    - short description for command listing
     :option-spec - clojure.tools.cli option spec
     :run-fn  - (fn [parsed-opts]) to execute the command"
  [{:keys [name] :as cmd}]
  (swap! commands assoc name cmd))

(defn get-command [name]
  (get @commands name))

(defn all-commands []
  (sort-by :name (vals @commands)))

(defn- subcommand-summary [subcommands]
  (let [max-len (apply max (map #(count (:name %)) subcommands))]
    (str/join "\n" (map (fn [{:keys [name desc]}]
                           (str "  " name
                                (apply str (repeat (- (+ max-len 4) (count name)) " "))
                                desc))
                         subcommands))))

(defn command-help [cmd]
  (if-let [help-text (:help-text cmd)]
    (if (fn? help-text) (help-text) help-text)
    (let [summary     (when-let [option-spec (:option-spec cmd)]
                        (-> (tools-cli/parse-opts [] option-spec)
                            :summary
                            str/trim-newline))
          subcommands (:subcommands cmd)]
      (str/join "\n"
                (concat [(str "Usage: isaac " (:usage cmd))
                         ""
                         (:desc cmd)]
                        (when-not (str/blank? summary)
                          [""
                           "Options:"
                           summary])
                        (when (seq subcommands)
                          [""
                           "Subcommands:"
                           (subcommand-summary subcommands)]))))))

;; endregion ^^^^^ Command Registry ^^^^^

;; region ----- Init Command -----

(defn- runtime-fs [opts] (fs/instance opts))

(defn- write-edn! [fs* path value]
  (fs/mkdirs fs* (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit fs* path (with-out-str (pprint/pprint value)))))

(defn- write-markdown-entity! [fs* path config body]
  (fs/mkdirs fs* (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit fs* path (str "---\n"
                           (with-out-str (pprint/pprint config))
                           "---\n\n"
                           body))))

(defn- isaac-edn-path [home]
  (paths/root-config-file home))

(defn- created-files []
  ["config/isaac.edn"
   "config/crew/main.md"
   "config/models/llama.edn"
   "config/providers/ollama.edn"
   "config/cron/heartbeat.md"])

(defn- scaffold! [home fs*]
  (write-edn! fs* (paths/config-path home "isaac.edn")
               {:defaults            {:crew :main :model :llama}
                :tz                  "America/Chicago"
                :prefer-entity-files true})
  (write-markdown-entity! fs* (paths/config-path home "crew/main.md")
                           {:model :llama}
                           "You are Isaac, a helpful AI assistant.")
  (write-edn! fs* (paths/config-path home "models/llama.edn") {:model "llama3.2" :provider :ollama})
  (write-edn! fs* (paths/config-path home "providers/ollama.edn") {:base-url "http://localhost:11434" :api :ollama})
  (write-markdown-entity! fs* (paths/config-path home "cron/heartbeat.md")
                           {:expr "*/30 * * * *" :crew :main}
                           "Heartbeat. Anything worth noting?"))

(defn- print-success! [display-home]
  (println (str "Isaac initialized at " display-home "."))
  (println)
  (println "Created:")
  (doseq [path (created-files)]
    (println (str "  " path)))
  (println)
  (println "Isaac uses Ollama locally. If you don't have it:")
  (println)
  (println "  brew install ollama")
  (println "  ollama serve &")
  (println "  ollama pull llama3.2")
  (println)
  (println "Then try:")
  (println)
  (println "  isaac prompt -m \"hello\""))

(defn init-help []
  (str "Usage: isaac init\n\n"
       "Scaffold a default Isaac config for a fresh install."))

(defn init-run [{:keys [display-home home] :as opts}]
  (let [fs*  (runtime-fs opts)
        path (isaac-edn-path home)]
    (if (fs/exists? fs* path)
      (do
        (binding [*out* *err*]
          (println (str "config already exists at " path "; edit it directly.")))
        1)
      (do
        (scaffold! home fs*)
        (print-success! (or display-home home))
        0))))

(defn init-run-fn [{:keys [display-home home _raw-args fs]}]
  (if (some #(or (= "--help" %) (= "-h" %)) (or _raw-args []))
    (do
      (println (init-help))
      0)
    (init-run {:display-home display-home :home home :fs fs})))

(register!
  {:name      "init"
   :usage     "init"
   :desc      "Scaffold a default Isaac config"
   :help-text init-help
   :run-fn    init-run-fn})

;; endregion ^^^^^ Init Command ^^^^^

;; region ----- Module Command Management -----

(defonce ^:private module-command-names* (atom #{}))

(defn- wrap-module-run-fn [{:keys [run-fn] :as cmd}]
  (let [help-cmd (dissoc cmd :run-fn)]
    (fn [{:keys [_raw-args] :as opts}]
      (if (some #{"--help" "-h"} (or _raw-args []))
        (do
          (println (command-help help-cmd))
          0)
        (run-fn opts)))))

(defn register-module-command!
  "Register a module-contributed CLI command. Tracked separately so
    clear-module-commands! can remove only module-contributed entries."
  [{:keys [name] :as cmd}]
  (swap! module-command-names* conj name)
  (register! (assoc cmd :run-fn (wrap-module-run-fn cmd))))

(defn clear-module-commands!
  "Remove all module-contributed commands registered via register-module-command!,
   leaving the core built-in commands intact."
  []
  (let [names @module-command-names*]
    (swap! commands #(apply dissoc % names))
    (reset! module-command-names* #{})))

;; endregion ^^^^^ Module Command Management ^^^^^
