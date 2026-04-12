(ns isaac.cli.chat.toad
  (:require [clojure.string :as str]))

(defn build-toad-command []
  {:command "toad"
   :args    ["acp" "isaac acp" "."]
   :env     {}})

(defn format-toad-command []
  (let [{:keys [command args]} (build-toad-command)]
    (str/join " " (cons command (map #(if (str/includes? % " ") (str "\"" % "\"") %) args)))))

(defn spawn-toad! []
  (let [{:keys [command args]} (build-toad-command)
        pb (ProcessBuilder. (into [command] args))]
    (.inheritIO pb)
    (-> (.start pb) .waitFor)))
