(ns isaac.cli.registry
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]))

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
           (str "\n" summary)))))
