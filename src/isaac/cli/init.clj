(ns isaac.cli.init
  (:require
    [clojure.pprint :as pprint]
    [isaac.cli.registry :as registry]
    [isaac.fs :as fs]))

(defn- write-edn! [path value]
  (fs/mkdirs (fs/parent path))
  (binding [*print-namespace-maps* false]
    (fs/spit path (with-out-str (pprint/pprint value)))))

(defn- config-root [home]
  (str home "/.isaac/config"))

(defn- isaac-edn-path [home]
  (str (config-root home) "/isaac.edn"))

(defn- scaffold! [home]
  (let [root (config-root home)]
    (write-edn! (str root "/isaac.edn")
                {:defaults              {:crew :main :model :default}
                 :tz                    "America/Chicago"
                 :prefer-entity-files?  true
                 :cron                  {:heartbeat {:expr   "0 0 * * *"
                                                    :crew   :main
                                                    :prompt "Daily heartbeat. Anything worth noting?"}}})
    (write-edn! (str root "/crew/main.edn") {:model :default})
    (fs/mkdirs (str root "/crew"))
    (fs/spit (str root "/crew/main.md") "You are Isaac, a helpful AI assistant.")
    (write-edn! (str root "/models/default.edn") {:model "llama3.2" :provider :ollama})
    (write-edn! (str root "/providers/ollama.edn") {:base-url "http://localhost:11434" :api "ollama"})))

(defn help []
  (str "Usage: isaac init\n\n"
       "Scaffold a default Isaac config for a fresh install."))

(defn run [{:keys [home]}]
  (let [path (isaac-edn-path home)]
    (if (fs/exists? path)
      (do
        (binding [*out* *err*]
          (println (str "config already exists at " path "; edit it directly, or remove and re-run init.")))
        1)
      (do
        (scaffold! home)
        (println (str "Isaac initialized at " home "."))
        (println "brew install ollama")
        (println "ollama serve")
        (println "ollama pull llama3.2")
        (println "then isaac 'hello'")
        0))))

(defn run-fn [{:keys [home _raw-args]}]
  (if (some #(or (= "--help" %) (= "-h" %)) (or _raw-args []))
    (do
      (println (help))
      0)
    (run {:home home})))

(registry/register!
  {:name      "init"
   :usage     "init"
   :desc      "Scaffold a default Isaac config"
   :help-text help
   :run-fn    run-fn})
