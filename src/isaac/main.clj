(ns isaac.main
  (:require
    [isaac.cli.chat :as chat]))

(defn -main [& args]
  (let [cmd    (first args)
        opts   (rest args)
        parsed (loop [remaining opts
                      result    {}]
                 (if (empty? remaining)
                   result
                   (let [[flag & rest-args] remaining]
                     (case flag
                       "--agent"   (recur (rest rest-args) (assoc result :agent (first rest-args)))
                       "--resume"  (recur rest-args (assoc result :resume true))
                       "--session" (recur (rest rest-args) (assoc result :session (first rest-args)))
                       (recur rest-args result)))))]
    (case cmd
      "chat" (chat/run parsed)
      (do (println "Usage: isaac <command>")
          (println)
          (println "Commands:")
          (println "  chat    Start an interactive chat session")))))
