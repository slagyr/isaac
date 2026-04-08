(ns isaac.features.steps.tools
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as registry]))

;; region ----- Helpers -----

(defn- state-dir [] (g/get :state-dir))

(defn- resolve-path [p]
  (if (str/starts-with? p "/") p (str (state-dir) "/" p)))

(defn- result-text []
  (let [r (g/get :tool-result)]
    (or (:result r) (:error r) "")))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Registration -----

(defgiven builtin-tools-registered "the built-in tools are registered"
  []
  (registry/clear!)
  (builtin/register-all! registry/register!))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- File / Directory Setup -----

(defgiven clean-test-dir "a clean test directory {dir:string}"
  [dir]
  (let [f (io/file dir)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))
    (.mkdirs f)
    (g/assoc! :state-dir dir)))

(defgiven file-with-content "a file {name:string} exists with content {content:string}"
  [name content]
  (let [path   (resolve-path name)
        actual (str/replace content "\\n" "\n")]
    (.mkdirs (.getParentFile (io/file path)))
    (spit path actual)))

(defgiven file-with-lines #"a file \"([^\"]+)\" exists with (\d+) lines"
  [name n]
  (let [path  (resolve-path name)
        lines (str/join "\n" (map #(str "line " %) (range 1 (inc (parse-long n)))))]
    (spit path lines)))

(defgiven dir-with-files #"a directory \"([^\"]+)\" exists with files (.+)"
  [dir-name files-str]
  (let [dir-path   (resolve-path dir-name)
        _          (.mkdirs (io/file dir-path))
        file-names (mapv second (re-seq #"\"([^\"]+)\"" files-str))]
    (doseq [f file-names]
      (spit (str dir-path "/" f) ""))))

(defgiven exec-timeout "the exec timeout is set to {n:int} milliseconds"
  [n]
  (g/assoc! :exec-timeout n))

;; endregion ^^^^^ File / Directory Setup ^^^^^

;; region ----- Tool Execution -----

(defwhen tool-executed "tool {name:string} is executed with:"
  [name table]
  (let [args (into {} (map (fn [[k v]] [(keyword k) v]) (:rows table)))
        result (registry/execute name args)]
    (g/assoc! :tool-result result)))

(defwhen tool-called "the tool {name:string} is called with:"
  [tool-name table]
  (registry/clear!)
  (builtin/register-all! registry/register!)
  (let [timeout  (g/get :exec-timeout)
        all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]]
                                 (let [key (keyword k)
                                       val (case key
                                             :filePath   (resolve-path v)
                                             :workdir    (resolve-path v)
                                             :offset     (parse-long v)
                                             :limit      (parse-long v)
                                             :replaceAll (= "true" v)
                                             :timeout    (parse-long v)
                                             v)]
                                   [key val]))
                               all-rows))
        args     (if timeout (assoc args :timeout timeout) args)
        result   (registry/execute tool-name args)]
    (g/assoc! :tool-result result)))

;; endregion ^^^^^ Tool Execution ^^^^^

;; region ----- Assertions -----

(defthen tool-result-contains "the tool result contains {text:string}"
  [text]
  (g/should (str/includes? (result-text) text)))

(defthen tool-result-not-contains "the tool result does not contain {text:string}"
  [text]
  (g/should-not (str/includes? (result-text) text)))

(defthen tool-result-not-error "the tool result is not an error"
  []
  (g/should-not (:isError (g/get :tool-result))))

(defthen tool-result-is-error "the tool result is an error"
  []
  (g/should (:isError (g/get :tool-result))))

(defthen file-has-content "the file {name:string} has content {content:string}"
  [name content]
  (let [path   (resolve-path name)
        actual (slurp path)
        expect (str/replace content "\\n" "\n")]
    (g/should= expect actual)))

;; endregion ^^^^^ Assertions ^^^^^
