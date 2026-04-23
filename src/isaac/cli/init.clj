(ns isaac.cli.init
  (:require
    [clojure.pprint :as pprint]
    [isaac.cli.registry :as registry]
    [isaac.config.paths :as paths]
    [isaac.fs :as fs]))

(defn- write-edn! [path value]
  (fs/mkdirs (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit path (with-out-str (pprint/pprint value)))))

(defn- isaac-edn-path [home]
  (paths/root-config-file home))

(defn- created-files []
  ["config/isaac.edn"
   "config/crew/main.edn"
   "config/crew/main.md"
   "config/models/llama.edn"
   "config/providers/ollama.edn"
   "config/cron/heartbeat.edn"
   "config/cron/heartbeat.md"])

(defn- scaffold! [home]
  (write-edn! (paths/config-path home "isaac.edn")
              {:defaults             {:crew :main :model :llama}
               :tz                   "America/Chicago"
               :prefer-entity-files? true
               :cron                 {:heartbeat {:expr "*/30 * * * *"
                                                 :crew :main}}})
  (write-edn! (paths/config-path home "crew/main.edn") {:model :llama})
  (fs/mkdirs (str (paths/config-root home) "/crew"))
  (fs/spit (paths/config-path home "crew/main.md") "You are Isaac, a helpful AI assistant.")
  (write-edn! (paths/config-path home "models/llama.edn") {:model "llama3.2" :provider :ollama})
  (write-edn! (paths/config-path home "providers/ollama.edn") {:base-url "http://localhost:11434" :api :ollama})
  (write-edn! (paths/config-path home "cron/heartbeat.edn") {:expr "*/30 * * * *" :crew :main})
  (fs/mkdirs (str (paths/config-root home) "/cron"))
  (fs/spit (paths/config-path home "cron/heartbeat.md") "Heartbeat. Anything worth noting?"))

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

(defn help []
  (str "Usage: isaac init\n\n"
       "Scaffold a default Isaac config for a fresh install."))

(defn run [{:keys [display-home home]}]
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

(defn run-fn [{:keys [display-home home _raw-args]}]
  (if (some #(or (= "--help" %) (= "-h" %)) (or _raw-args []))
    (do
      (println (help))
      0)
    (run {:display-home display-home :home home})))

(registry/register!
  {:name      "init"
   :usage     "init"
   :desc      "Scaffold a default Isaac config"
   :help-text help
   :run-fn    run-fn})
