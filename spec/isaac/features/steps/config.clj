(ns isaac.features.steps.config
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen helper!]]
    [isaac.config.loader :as loader]
    [isaac.fs :as fs]
    [isaac.server.app :as app]))

(helper! isaac.features.steps.config)

;; region ----- Helpers -----

(defn- state-dir []
  (or (g/get :state-dir) "/isaac-state"))

(defn- config-root []
  (str (state-dir) "/.isaac/config"))

(defn- mem-fs []
  (or (g/get :mem-fs)
      (let [mem (fs/mem-fs)]
        (g/assoc! :mem-fs mem)
        mem)))

(defn- with-config-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

(defn- load-result []
  (with-config-fs #(loader/load-config-result {:home (state-dir)})))

(defn- parse-expected [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    :else                        value))

(defn- actual->string [value]
  (cond
    (keyword? value) (name value)
    :else            (str value)))

(defn- get-path [data path]
  (reduce (fn [current segment]
            (cond
              (nil? current) nil
              (map? current) (or (get current (keyword segment))
                                 (get current segment))
              (vector? current) (nth current (parse-long segment) nil)
              :else nil))
          data
          (str/split path #"\.")))

(defn- matching-messages [entries table]
  (mapv (fn [row]
          (zipmap (:headers table) row))
        (:rows table)))

(defn- row-matches? [entry expected]
  (and (= (:key entry) (get expected "key"))
       (re-find (re-pattern (get expected "value")) (:value entry))))

(defn- config-file-path [path]
  (str (config-root) "/" path))

(defn- isaac-env-path []
  (str (state-dir) "/.isaac/.env"))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given step bodies -----

(defn config-file-containing [path content]
  (with-config-fs
    (fn []
      (let [full-path (str (config-root) "/" path)]
        (fs/mkdirs (or (fs/parent full-path) (config-root)))
        (fs/spit full-path (str/trim content))))))

(defn environment-variable-is [name value]
  (loader/set-env-override! name value)
  (c3env/override! name value))

(defn isaac-env-file-contains [content]
  (with-config-fs
    (fn []
      (fs/spit (isaac-env-path) (str/trim content)))))

;; endregion ^^^^^ Given step bodies ^^^^^

;; region ----- Then step bodies -----

(defn loaded-config-has [table]
  (let [config (or (app/current-config)
                   (:config (load-result)))]
    (doseq [row (:rows table)]
      (let [m        (zipmap (:headers table) row)
            actual   (get-path config (get m "key"))
            expected (parse-expected (get m "value"))]
        (if (string? expected)
          (g/should= expected (actual->string actual))
          (g/should= expected actual))))))

(defn config-has-validation-errors [table]
  (let [errors   (:errors (load-result))
        expected (matching-messages errors table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) errors)))))

(defn config-has-validation-warnings [table]
  (let [warnings (:warnings (load-result))
        expected (matching-messages warnings table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) warnings)))))

(defn config-file-matches [path table]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp (config-file-path path)) "")]
        (doseq [row (:rows table)]
          (g/should (re-find (re-pattern (str/trim (first row))) content)))))))

(defn config-file-does-not-contain [path expected]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp (config-file-path path)) "")]
        (g/should-not (str/includes? content expected))))))

(defn config-file-does-not-exist [path]
  (with-config-fs
    (fn []
      (g/should-not (fs/exists? (config-file-path path))))))

;; endregion ^^^^^ Then step bodies ^^^^^

;; region ----- Routing -----

(defgiven "config file {path:string} containing:" config/config-file-containing
  "Writes the heredoc content to <state-dir>/.isaac/config/<path>. Uses
   the in-memory fs. Path is config-root-relative, e.g. 'isaac.edn' or
   'crew/marvin.edn'.")

(defgiven "environment variable {name:string} is {value:string}" config/environment-variable-is
  "Sets BOTH the loader env-override (used by ${VAR} substitution) AND
   c3env's override (used by any c3env/env call). Covers both entry
   points so tests don't rely on which one the code happens to use.")

(defgiven "the isaac .env file contains:" config/isaac-env-file-contains
  "Writes the heredoc content to <state-dir>/.isaac/.env. This is the
   file the loader reads for ${VAR} substitution.")

(defthen "the loaded config has:" config/loaded-config-has
  "Prefers the running server's in-memory cfg (hot-reload-aware) via
   app/current-config; falls back to a fresh load-config against the
   state-dir when no server is up. Rows use dot-path keys, e.g.
   'crew.marvin.soul'.")

(defthen "the config has validation errors matching:" config/config-has-validation-errors)

(defthen "the config has validation warnings matching:" config/config-has-validation-warnings)

(defthen "the config file {path:string} matches:" config/config-file-matches
  "Reads the on-disk config file content (state-dir-relative path under
   config-root). Each row is a regex pattern; all must match somewhere
   in the file. Order and structure are not enforced.")

(defthen "the config file {path:string} does not contain {expected:string}" config/config-file-does-not-contain)

(defthen "the config file {path:string} does not exist" config/config-file-does-not-exist)

;; endregion ^^^^^ Routing ^^^^^
