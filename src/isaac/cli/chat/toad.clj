(ns isaac.cli.chat.toad
  (:require [clojure.string :as str]))

(defn build-toad-command [& [{:keys [agent model remote resume session token]}]]
  (let [acp-cmd (cond-> "isaac acp"
                   agent (str " --agent " agent)
                   model (str " --model " model)
                   resume (str " --resume")
                   session (str " --session " session)
                   remote (str " --remote " remote)
                   token (str " --token " token))]
    {:command "toad"
     :args    ["acp" acp-cmd "."]
     :env     {}}))

(defn format-toad-command [& [opts]]
  (let [{:keys [command args]} (build-toad-command opts)]
    (str/join " " (cons command (map #(if (str/includes? % " ") (str "\"" % "\"") %) args)))))

(defn spawn-toad! [& [opts]]
  (let [{:keys [command args]} (build-toad-command opts)
        pb (ProcessBuilder. (into [command] args))]
    (.inheritIO pb)
    (-> (.start pb) .waitFor)))
