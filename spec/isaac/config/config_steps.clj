(ns isaac.config.config-steps
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen defwhen helper!]]
    [isaac.config.loader :as loader]
    [isaac.fs :as fs]
    [isaac.module.loader :as module-loader]
    [isaac.server.app :as app]
    [isaac.system :as system]))

(helper! isaac.config.config-steps)

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
  (system/with-nested-system {:fs (mem-fs)}
    (f)))

(defn- path-exists? [path]
  (or (fs/exists?- (or (g/get :mem-fs) (system/get :fs) (fs/real-fs)) path)
      (.exists (java.io.File. path))))

(defn- module-manifest-path [id]
  (some (fn [root]
          (some #(when (path-exists? %) %)
                [(str root "/resources/isaac-manifest.edn")
                 (str root "/src/isaac-manifest.edn")]))
        [(str (state-dir) "/.isaac/modules/" (name id))
         (str (System/getProperty "user.dir") "/modules/" (name id))]))

(defn- load-config-result []
  (let [real-manifest-resource @#'isaac.module.loader/manifest-resource
         base-cwd               (System/getProperty "user.dir")
         override               (g/get :effective-cwd)
         effective-cwd          (when override
                                   (if (str/starts-with? override "/")
                                     override
                                     (str base-cwd "/" override)))
         fs*                    (or (g/get :mem-fs) (system/get :fs) (fs/real-fs))]
    (try
      (when effective-cwd
        (System/setProperty "user.dir" effective-cwd))
      (with-redefs [module-loader/add-module-deps! (fn [_ _])
                    module-loader/manifest-resource (fn [id]
                                                      (or (module-manifest-path id)
                                                          (real-manifest-resource id)))]
        (loader/load-config-result {:home (state-dir) :fs fs*}))
      (finally
        (System/setProperty "user.dir" base-cwd)))))

(defn- load-result []
  (or (g/get :loaded-config-result)
      (if-let [mem (g/get :mem-fs)]
        (system/with-nested-system {:fs mem}
          (load-config-result))
        (load-config-result))))

(defn- parse-expected [value]
  (cond
    (re-matches #"-?\d+" value) (parse-long value)
    :else                        value))

(defn- actual->string [value]
  (cond
    (keyword? value) (name value)
    :else            (str value)))

(defn- get-path [data path]
  (let [segments (if (str/starts-with? path "/")
                   (remove str/blank? (str/split (subs path 1) #"/"))
                   (str/split path #"\."))]
    (reduce (fn [current segment]
              (cond
                (nil? current) nil
                (map? current) (or (get current (keyword segment))
                                   (get current segment))
                (vector? current) (nth current (parse-long segment) nil)
                :else nil))
            data
            segments)))

(defn- matching-messages [table]
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

(defn effective-cwd-is [path]
  (g/assoc! :effective-cwd path))

(defn config-file-containing [path content]
  (with-config-fs
    (fn []
      (let [full-path (str (config-root) "/" path)
            fs*       (system/get :fs)]
        (fs/mkdirs- fs* (or (fs/parent full-path) (config-root)))
        (fs/spit-   fs* full-path (str/trim content))))))

(defn environment-variable-is [name value]
  (loader/set-env-override! name value)
  (c3env/override! name value))

(defn isaac-env-file-contains [content]
  (with-config-fs
    (fn []
      (fs/spit- (system/get :fs) (isaac-env-path) (str/trim content)))))

;; endregion ^^^^^ Given step bodies ^^^^^

;; region ----- When step bodies -----

(defn config-is-loaded []
  (let [result (load-result)]
    (g/assoc! :loaded-config-result result)))

;; endregion ^^^^^ When step bodies ^^^^^

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
        expected (matching-messages table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) errors)))))

(defn config-has-validation-warnings [table]
  (let [warnings (:warnings (load-result))
        expected (matching-messages table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) warnings)))))

(defn config-file-matches [path table]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp- (system/get :fs) (config-file-path path)) "")]
        (doseq [row (:rows table)]
          (g/should (re-find (re-pattern (str/trim (first row))) content)))))))

(defn config-file-does-not-contain [path expected]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp- (system/get :fs) (config-file-path path)) "")]
        (g/should-not (str/includes? content expected))))))

(defn config-file-does-not-exist [path]
  (with-config-fs
    (fn []
      (g/should-not (fs/exists?- (system/get :fs) (config-file-path path))))))

(defn config-has-no-validation-errors []
  (g/should= [] (:errors (load-result))))

(defn config-has-no-validation-warnings []
  (g/should= [] (:warnings (load-result))))

;; endregion ^^^^^ Then step bodies ^^^^^

;; region ----- Routing -----

(defgiven "the effective working directory is {path:string}" isaac.config.config-steps/effective-cwd-is
  "Sets the working directory used during config loading. Relative paths are
   resolved against the JVM's actual working directory. Use this to test
   :local/root \".\" module discovery (where the module root equals the cwd).")

(defgiven "config file {path:string} containing:" isaac.config.config-steps/config-file-containing
  "Writes the heredoc content to <state-dir>/.isaac/config/<path>. Uses
   the in-memory fs. Path is config-root-relative, e.g. 'isaac.edn' or
   'crew/marvin.edn'.")

(defgiven "environment variable {name:string} is {value:string}" isaac.config.config-steps/environment-variable-is
  "Sets BOTH the loader env-override (used by ${VAR} substitution) AND
    c3env's override (used by any c3env/env call). Covers both entry
    points so tests don't rely on which one the code happens to use.")

(defgiven #"the env var \"([^\"]+)\" is set to \"([^\"]+)\"" isaac.config.config-steps/environment-variable-is)

(defgiven "the isaac .env file contains:" isaac.config.config-steps/isaac-env-file-contains
  "Writes the heredoc content to <state-dir>/.isaac/.env. This is the
   file the loader reads for ${VAR} substitution.")

(defwhen "the config is loaded" isaac.config.config-steps/config-is-loaded
  "Triggers a fresh load-config-result against the state-dir and caches
   the result so subsequent Then steps (loaded-config-has, validation
   errors) use the same load.")

(defthen "the loaded config has:" isaac.config.config-steps/loaded-config-has
  "Prefers the running server's in-memory cfg (hot-reload-aware) via
   app/current-config; falls back to a fresh load-config against the
   state-dir when no server is up. Rows use dot-path keys, e.g.
   'crew.marvin.soul'.")

(defthen "the config has validation errors matching:" isaac.config.config-steps/config-has-validation-errors)

(defthen "the config has validation warnings matching:" isaac.config.config-steps/config-has-validation-warnings)

(defthen "the config file {path:string} matches:" isaac.config.config-steps/config-file-matches
  "Reads the on-disk config file content (state-dir-relative path under
   config-root). Each row is a regex pattern; all must match somewhere
   in the file. Order and structure are not enforced.")

(defthen "the config file {path:string} does not contain {expected:string}" isaac.config.config-steps/config-file-does-not-contain)

(defthen "the config file {path:string} does not exist" isaac.config.config-steps/config-file-does-not-exist)

(defthen "the config has no validation errors" isaac.config.config-steps/config-has-no-validation-errors)

(defthen "the config has no validation warnings" isaac.config.config-steps/config-has-no-validation-warnings)

;; endregion ^^^^^ Routing ^^^^^
