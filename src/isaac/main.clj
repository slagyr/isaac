(ns isaac.main
  (:require
    [clojure.string :as str]
    [isaac.cli.registry :as registry]
    isaac.cli.auth
    isaac.cli.chat))

(defn- usage []
  (let [cmds (registry/all-commands)
        max-len (apply max (map #(count (:name %)) cmds))]
    (str "Usage: isaac <command> [options]\n\n"
         "Commands:\n"
         (str/join "\n" (map (fn [cmd]
                               (str "  " (:name cmd)
                                    (apply str (repeat (- (+ max-len 4) (count (:name cmd))) " "))
                                    (:desc cmd)))
                             cmds)))))

(defn- parse-opts [args]
  (loop [remaining args
         result    {}]
    (if (empty? remaining)
      result
      (let [[flag & rest-args] remaining]
        (case flag
          "--agent"   (recur (rest rest-args) (assoc result :agent (first rest-args)))
          "--model"   (recur (rest rest-args) (assoc result :model (first rest-args)))
          "--resume"  (recur rest-args (assoc result :resume true))
          "--session" (recur (rest rest-args) (assoc result :session (first rest-args)))
          (recur rest-args result))))))

(defn- resolve-alias
  "Resolve command aliases. 'models auth ...' → 'auth ...'"
  [args]
  (if (and (= "models" (first args)) (= "auth" (second args)))
    (rest args)
    args))

(defn run
  "Run the CLI. Returns exit code."
  [args]
  (let [args (resolve-alias args)
        cmd  (first args)
        opts (rest args)]
    (cond
      (or (nil? cmd) (str/blank? cmd))
      (do (println (usage)) 0)

      (= "help" cmd)
      (if-let [target (first opts)]
        (if-let [command (registry/get-command target)]
          (do (println (registry/command-help command)) 0)
          (do (println (str "Unknown command: " target)) 1))
        (do (println (usage)) 0))

      :else
      (if-let [command (registry/get-command cmd)]
        (if (some #{"--help"} opts)
          (do (println (registry/command-help command)) 0)
          (or ((:run-fn command) (assoc (parse-opts opts) :_raw-args (vec opts))) 0))
        (do (println (str "Unknown command: " cmd))
            (println (usage))
            1)))))

(defn -main [& args]
  (let [exit-code (run args)]
    (when (pos? exit-code)
      (System/exit exit-code))))
