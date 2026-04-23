(ns isaac.features.steps.config
  (:require
    [c3kit.apron.env :as c3env]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defthen]]
    [isaac.config.loader :as loader]
    [isaac.fs :as fs]
    [isaac.server.app :as app]))

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
  (str (state-dir) "/.env"))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given -----

(defgiven config-file-containing "config file {path:string} containing:"
  [path content]
  (with-config-fs
    (fn []
      (let [full-path (str (config-root) "/" path)]
        (fs/mkdirs (or (fs/parent full-path) (config-root)))
        (fs/spit full-path (str/trim content))))))

(defgiven environment-variable-is "environment variable {name:string} is {value:string}"
  [name value]
  (loader/set-env-override! name value)
  (c3env/override! name value))

(defgiven isaac-env-file-contains "the isaac .env file contains:"
  [content]
  (with-config-fs
    (fn []
      (fs/spit (isaac-env-path) (str/trim content)))))

;; endregion ^^^^^ Given ^^^^^

;; region ----- Then -----

(defthen loaded-config-has "the loaded config has:"
  [table]
  (let [config (or (app/current-config)
                   (:config (load-result)))]
    (doseq [row (:rows table)]
      (let [m        (zipmap (:headers table) row)
            actual   (get-path config (get m "key"))
            expected (parse-expected (get m "value"))]
        (if (string? expected)
          (g/should= expected (actual->string actual))
          (g/should= expected actual))))))

(defthen config-has-validation-errors "the config has validation errors matching:"
  [table]
  (let [errors   (:errors (load-result))
        expected (matching-messages errors table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) errors)))))

(defthen config-has-validation-warnings "the config has validation warnings matching:"
  [table]
  (let [warnings (:warnings (load-result))
        expected (matching-messages warnings table)]
    (doseq [row expected]
      (g/should (some #(row-matches? % row) warnings)))))

(defthen config-file-matches "the config file {path:string} matches:"
  [path table]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp (config-file-path path)) "")]
        (doseq [row (:rows table)]
          (g/should (re-find (re-pattern (str/trim (first row))) content)))))))

(defthen config-file-does-not-contain "the config file {path:string} does not contain {expected:string}"
  [path expected]
  (with-config-fs
    (fn []
      (let [content (or (fs/slurp (config-file-path path)) "")]
        (g/should-not (str/includes? content expected))))))

(defthen config-file-does-not-exist "the config file {path:string} does not exist"
  [path]
  (with-config-fs
    (fn []
      (g/should-not (fs/exists? (config-file-path path))))))

;; endregion ^^^^^ Then ^^^^^
