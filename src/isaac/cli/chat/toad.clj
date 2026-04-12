(ns isaac.cli.chat.toad
  (:require [clojure.string :as str]))

(defn build-toad-command [& [{:keys [model agent]}]]
  (let [acp-cmd (cond-> "isaac acp"
                  agent (str " --agent " agent)
                  model (str " --model " model))]
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
