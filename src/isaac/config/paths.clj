(ns isaac.config.paths
  "Filesystem layout knowledge for Isaac config. Pure path
   construction — no I/O. Centralizes where config files live under
   ~/.isaac/config so loader and mutate don't each build paths from
   scratch.")

(def ^:private entity-file-pattern #"[^/]+/[^/]+\.edn")
(def ^:private markdown-file-pattern #"(crew|cron|hooks)/[^/]+\.md")

(def root-filename "isaac.edn")

(defn config-root [home]
  (str home "/.isaac/config"))

(defn config-path [home relative]
  (str (config-root home) "/" relative))

(defn root-config-file [home]
  (config-path home root-filename))

(defn entity-relative [kind id]
  (str (name kind) "/" id ".edn"))

(defn soul-relative [id]
  (str "crew/" id ".md"))

(defn cron-relative [id]
  (str "cron/" id ".md"))

(defn hook-relative [id]
  (str "hooks/" id ".md"))

(defn config-file? [relative-path]
  (and (string? relative-path)
       (or (= root-filename relative-path)
           (boolean (re-matches entity-file-pattern relative-path))
           (boolean (re-matches markdown-file-pattern relative-path)))))
