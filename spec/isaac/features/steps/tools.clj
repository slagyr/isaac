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

(defn- parse-tool-value [k v]
  (case k
    ("filePath" "path" "workdir") (resolve-path v)
    ("offset" "limit" "timeout" "head_limit" "-A" "-B" "-C") (parse-long v)
    ("replaceAll" "-i" "-n" "multiline") (= "true" v)
    v))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Registration -----

(defgiven builtin-tools-registered "the built-in tools are registered"
  []
  (registry/clear!)
  (builtin/register-all! registry/register!))

(defgiven nil-tool-registered #"a tool \"([^\"]+)\" that returns nil is registered"
  [name]
  (registry/register! {:name name :description "Returns nil" :handler (fn [_] nil)}))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- File / Directory Setup -----

(defgiven clean-test-dir "a clean test directory {dir:string}"
  [dir]
  (let [abs-dir (if (str/starts-with? dir "/")
                  dir
                  (str (System/getProperty "user.dir") "/" dir))
        f       (io/file abs-dir)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))
    (.mkdirs f)
    (g/assoc! :state-dir abs-dir)))

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

(defgiven binary-file-exists "a binary file {name:string} exists"
  [name]
  (let [path  (resolve-path name)
        bytes (byte-array (map unchecked-byte
                               [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
                                0x00 0x00 0x00 0x0D 0x49 0x48 0x44 0x52]))]
    (.mkdirs (.getParentFile (io/file path)))
    (with-open [out (io/output-stream (io/file path))]
      (.write out bytes))))

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
  (let [all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]] [(keyword k) v]) all-rows))
        result   (registry/execute name args)]
    (g/assoc! :tool-result result)))

(defwhen tool-called "the tool {name:string} is called with:"
  [tool-name table]
  (registry/clear!)
  (builtin/register-all! registry/register!)
  (let [timeout  (g/get :exec-timeout)
        all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]]
                                 [(keyword k) (parse-tool-value k v)])
                                all-rows))
        args     (if timeout (assoc args :timeout timeout) args)
        result   (registry/execute tool-name args)]
    (g/assoc! :tool-result result)))

;; endregion ^^^^^ Tool Execution ^^^^^

;; region ----- Assertions -----

(defthen tool-result-contains "the tool result contains {text:string}"
  [text]
  (g/should (str/includes? (result-text) text)))

(defthen tool-result-contains-table "the tool result contains:"
  [table]
  (doseq [row (:rows table)]
    (g/should (str/includes? (result-text) (or (get (zipmap (:headers table) row) "text")
                                               (first row))))))

(defthen tool-result-not-contains "the tool result does not contain {text:string}"
  [text]
  (g/should-not (str/includes? (result-text) text)))

(defthen tool-result-not-error "the tool result is not an error"
  []
  (g/should-not (:isError (g/get :tool-result))))

(defthen tool-result-is-error "the tool result is an error"
  []
  (g/should (:isError (g/get :tool-result))))

(defthen tool-result-indicates-error "the tool result should indicate an error"
  []
  (g/should (:isError (g/get :tool-result))))

(defthen file-has-content "the file {name:string} has content {content:string}"
  [name content]
  (let [path   (resolve-path name)
        actual (slurp path)
        expect (str/replace content "\\n" "\n")]
    (g/should= expect actual)))

;; endregion ^^^^^ Assertions ^^^^^
