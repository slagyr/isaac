(ns isaac.bean-gate.git
  "Thin wrappers over the git CLI. Every call names the repo directory."
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn run
  "Runs git in dir. Returns {:exit :out :err} without throwing."
  [dir & args]
  (let [{:keys [exit out err]} (apply p/shell {:dir (str dir) :out :string :err :string :continue true}
                                      "git" args)]
    {:exit exit :out out :err err}))

(defn git
  "Runs git in dir and returns trimmed stdout; throws on a non-zero exit."
  [dir & args]
  (let [{:keys [exit out err]} (apply run dir args)]
    (if (zero? exit)
      (str/trimr out)
      (throw (ex-info (str "git " (str/join " " args) ": " (str/trim err))
                      {:dir (str dir) :args args :exit exit})))))

(defn ok? [dir & args] (zero? (:exit (apply run dir args))))

(defn toplevel [dir] (git dir "rev-parse" "--show-toplevel"))


(defn resolve-rev
  "Full sha for rev, or nil when it does not resolve."
  [dir rev]
  (let [{:keys [exit out]} (run dir "rev-parse" "--verify" "--quiet" (str rev "^{object}"))]
    (when (zero? exit) (str/trim out))))

(defn ancestor? [dir a b] (ok? dir "merge-base" "--is-ancestor" a b))

(defn show-file
  "Contents of path at rev, or nil when the path does not exist there."
  [dir rev path]
  (let [{:keys [exit out]} (run dir "show" (str rev ":" path))]
    (when (zero? exit) out)))

(defn blob-id
  "Blob id of path at rev, or nil."
  [dir rev path]
  (let [{:keys [exit out]} (run dir "rev-parse" "--verify" "--quiet" (str rev ":" path))]
    (when (zero? exit) (str/trim out))))

(defn diff-name-status
  "[[status path]…] for the diff between revs a and b, limited to pathspec.
   nil when the diff fails (an unreachable rev, say)."
  [dir a b pathspec]
  (let [{:keys [exit out]} (run dir "diff" "--name-status" "--no-renames" a b "--" pathspec)]
    (when (zero? exit)
      (for [line (remove str/blank? (str/split-lines out))
            :let [[status path] (str/split (str/trimr line) #"\t" 2)]
            :when path]
        [status path]))))

(defn fetch!
  "Fetches origin. Returns nil on success, the error text otherwise."
  [dir]
  (let [{:keys [exit err]} (run dir "fetch" "--quiet" "origin")]
    (when-not (zero? exit) (str/trim err))))

(defn short-sha [sha] (subs sha 0 (min 7 (count sha))))
