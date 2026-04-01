(ns isaac.cli.registry)

(defonce ^:private commands (atom {}))

(defn register!
  "Register a CLI command.
   Options:
     :name    - command name (string)
     :usage   - usage line (e.g. \"isaac chat [options]\")
     :desc    - short description for command listing
     :options - seq of [flag description] pairs
     :run-fn  - (fn [parsed-opts]) to execute the command"
  [{:keys [name] :as cmd}]
  (swap! commands assoc name cmd))

(defn get-command [name]
  (get @commands name))

(defn all-commands []
  (sort-by :name (vals @commands)))

(defn command-help [cmd]
  (let [lines [(str "Usage: isaac " (:usage cmd))
               ""
               (:desc cmd)
               ""
               "Options:"]]
    (str (clojure.string/join "\n" lines)
         (when (seq (:options cmd))
           (str "\n" (clojure.string/join "\n"
                       (map (fn [[flag desc]]
                              (str "  " flag (apply str (repeat (max 1 (- 20 (count flag))) " ")) desc))
                            (:options cmd))))))))
