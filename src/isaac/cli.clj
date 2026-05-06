(ns isaac.cli
  (:require
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.config.paths :as paths]
    [isaac.fs :as fs]))

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

(defn command-help [cmd]
  (if-let [help-text (:help-text cmd)]
    (if (fn? help-text) (help-text) help-text)
    (let [summary (when-let [option-spec (:option-spec cmd)]
                    (-> (tools-cli/parse-opts [] option-spec)
                        :summary
                        str/trim-newline))
          lines   [(str "Usage: isaac " (:usage cmd))
                   ""
                   (:desc cmd)
                   ""
                   "Options:"]]
      (str (str/join "\n" lines)
           (when-not (str/blank? summary)
             (str "\n" summary))))))

;; endregion ^^^^^ Command Registry ^^^^^

;; region ----- Init Command -----

(defn- write-edn! [path value]
  (fs/mkdirs (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit path (with-out-str (pprint/pprint value)))))

(defn- write-markdown-entity! [path config body]
  (fs/mkdirs (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit path (str "---\n"
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

(defn- scaffold! [home]
  (write-edn! (paths/config-path home "isaac.edn")
              {:defaults            {:crew :main :model :llama}
               :tz                  "America/Chicago"
               :prefer-entity-files true})
  (write-markdown-entity! (paths/config-path home "crew/main.md")
                          {:model :llama}
                          "You are Isaac, a helpful AI assistant.")
  (write-edn! (paths/config-path home "models/llama.edn") {:model "llama3.2" :provider :ollama})
  (write-edn! (paths/config-path home "providers/ollama.edn") {:base-url "http://localhost:11434" :api :ollama})
  (write-markdown-entity! (paths/config-path home "cron/heartbeat.md")
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

(defn init-run [{:keys [display-home home]}]
  (let [path (isaac-edn-path home)]
    (if (fs/exists? path)
      (do
        (binding [*out* *err*]
          (println (str "config already exists at " path "; edit it directly.")))
        1)
      (do
        (scaffold! home)
        (print-success! (or display-home home))
        0))))

(defn init-run-fn [{:keys [display-home home _raw-args]}]
  (if (some #(or (= "--help" %) (= "-h" %)) (or _raw-args []))
    (do
      (println (init-help))
      0)
    (init-run {:display-home display-home :home home})))

(register!
  {:name      "init"
   :usage     "init"
   :desc      "Scaffold a default Isaac config"
   :help-text init-help
   :run-fn    init-run-fn})

;; endregion ^^^^^ Init Command ^^^^^
