(ns isaac.bean-gate.main
  (:require [clojure.string :as str]
            [isaac.bean-gate.core :as core]
            [isaac.bean-gate.git :as git]))

(def usage
  "Usage:
  bb bean-gate baseline <bean-id> <repo>:<path>[:<line>…] … [--dir <repo>=<path>]
  bb bean-gate verify <bean-id> [--dir <repo>=<path>] [--ref <repo>=<ref>]

baseline  (planner) Run from the isaac clone after the bean's @wip scenarios are on
          the module's main. Fetches origin, then appends to the bean body:
            feature-baseline: <repo> <origin/main sha>
            feature-blob: <repo> <path> <blob> [<line>,<line>]
          <line> names one of the bean's scenarios by its Scenario: line; without
          lines, every @wip scenario in the file is the bean's. Does not commit.
          Re-running appends new lines; the newest lines are in force.

verify    (worker, CI) Checks the bean against its baseline:
            1. baseline sha is on origin/main and each blob matches it
            2. contract lines (feature-*, ## Acceptance…, ## Exceptions) are append-only
            3. no baseline line was committed from a worker/verifier session
            4. every baselined block is intact (only @wip may change)
            5. the bean's scenarios no longer carry @wip
            6. the worker diff touches .feature files only by removing @wip
          Checks the bean's main-sha commit(s) when recorded, else HEAD (or --ref)
          against its merge-base with origin/main.
          Exit 0 pass, 1 fail, 2 not gated (no feature-baseline) or usage error.

Module checkouts default to ../<repo> beside the isaac clone; --dir overrides.")

(defn- parse-args
  "Splits argv into positional args and repeated --dir/--ref repo=value options."
  [args]
  (loop [[a & more] args acc {:positional [] :dirs {} :refs {} :errors []}]
    (cond
      (nil? a) acc
      (#{"-h" "--help"} a) (recur more (assoc acc :help true))
      (#{"--dir" "--ref"} a)
      (let [[repo v] (some-> (first more) (str/split #"=" 2))
            k        (if (= "--dir" a) :dirs :refs)]
        (if (and repo v (not (str/blank? v)))
          (recur (rest more) (assoc-in acc [k repo] v))
          (recur (rest more) (update acc :errors conj (str a " expects <repo>=<value>")))))
      (str/starts-with? a "--") (recur more (update acc :errors conj (str "unknown option " a)))
      :else (recur more (update acc :positional conj a)))))

(defn- print-lines [lines] (doseq [l lines] (println l)))

(defn- baseline! [root {:keys [positional dirs]}]
  (let [[_ id & raw] positional
        specs (map (juxt identity core/parse-spec) raw)
        bad   (keep (fn [[r s]] (when-not s r)) specs)]
    (cond
      (or (nil? id) (empty? raw)) (do (println usage) 2)
      (seq bad) (do (print-lines (map #(str "bean-gate: malformed spec " % " (want <repo>:<path>[:<line>…])") bad)) 2)
      :else
      (let [{:keys [ok? lines errors file]} (core/baseline {:root root :id id :specs (map second specs) :dirs dirs})]
        (if ok?
          (do (println (str "Appended to " file ":"))
              (print-lines (map #(str "  " %) lines))
              (println "Commit the bean to record the baseline.")
              0)
          (do (print-lines (map #(str "bean-gate baseline: " %) errors)) 1))))))

(defn- verify! [root {:keys [positional dirs refs]}]
  (let [[_ id] positional]
    (if (nil? id)
      (do (println usage) 2)
      (let [{:keys [status failures notes checked]} (core/verify {:root root :id id :dirs dirs :refs refs})]
        (print-lines (map #(str "note: " %) notes))
        (case status
          :ungated (do (println (str id ": no feature-baseline: use the verify path")) 2)
          :error   (do (print-lines (map #(str "bean-gate: " %) failures)) 2)
          :pass    (do (println (str id " bean-gate: PASS (" (str/join "; " checked) ")")) 0)
          :fail    (do (println (str id " bean-gate: FAIL (" (count failures) ")"))
                       (print-lines (map #(str "  FAIL " %) failures))
                       1))))))

(defn run
  "Runs the command; returns the exit code. opts :root overrides the isaac clone
   (default: the git toplevel of the working directory)."
  [args & [{:keys [root]}]]
  (let [{:keys [positional help errors] :as parsed} (parse-args args)
        cmd (first positional)]
    (cond
      (or help (nil? cmd)) (do (println usage) (if help 0 2))
      (seq errors) (do (print-lines (map #(str "bean-gate: " %) errors)) 2)
      :else
      (let [root (or root (try (git/toplevel ".") (catch Exception _ nil)))]
        (cond
          (nil? root) (do (println "bean-gate: run from inside the isaac clone") 2)
          (= "baseline" cmd) (baseline! root parsed)
          (= "verify" cmd) (verify! root parsed)
          :else (do (println (str "bean-gate: unknown subcommand " cmd)) (println usage) 2))))))

(defn -main [& args]
  (System/exit (run args)))
