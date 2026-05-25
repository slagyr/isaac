(ns isaac.config.paths
  "Filesystem layout knowledge for Isaac config. Pure path
   construction — no I/O. The state directory is the canonical root: config
   lives at <state-dir>/config and runtime data (crew, sessions, memory) under
   <state-dir>. In production the state dir defaults to ~/.isaac, but any
   directory is valid."
  (:require [clojure.string :as str]))

(def ^:private entity-file-pattern #"[^/]+/[^/]+\.edn")
(def ^:private markdown-file-pattern #"(crew|cron|hooks)/[^/]+\.md")

(def root-filename "isaac.edn")

(defn default-state-dir
  "The default state directory for a user home directory (~/.isaac)."
  [home]
  (str home "/.isaac"))

(defn config-root [state-dir]
  (str state-dir "/config"))

(defn config-path [state-dir relative]
  (str (config-root state-dir) "/" relative))

(defn root-config-file [state-dir]
  (config-path state-dir root-filename))

(defn entity-relative [kind id]
  (str (name kind) "/" id ".edn"))

(defn soul-relative [id]
  (str "crew/" id ".md"))

(defn cron-relative [id]
  (str "cron/" id ".md"))

(defn hook-relative [id]
  (str "hooks/" id ".md"))

(defn config-relative [state-dir path]
  (let [root-prefix (str (config-root state-dir) "/")]
    (when (str/starts-with? path root-prefix)
      (subs path (count root-prefix)))))

(defn config-file? [relative-path]
  (and (string? relative-path)
       (or (= root-filename relative-path)
           (boolean (re-matches entity-file-pattern relative-path))
           (boolean (re-matches markdown-file-pattern relative-path)))))
