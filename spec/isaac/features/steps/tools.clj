(ns isaac.features.steps.tools
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.glob :as glob]
    [isaac.tool.registry :as registry]))

;; region ----- Helpers -----

(defn- state-dir [] (g/get :state-dir))

(defn- resolve-path [p]
  (if (str/starts-with? p "/") p (str (state-dir) "/" p)))

(defn- result-text []
  (let [r (g/get :tool-result)]
    (or (:result r) (:error r) "")))

(defn- result-lines []
  (str/split-lines (result-text)))

(defn- parse-tool-value [k v]
  (case k
    ("filePath" "path" "workdir") (resolve-path v)
    ("offset" "limit" "timeout" "head_limit" "-A" "-B" "-C") (parse-long v)
    ("replaceAll" "-i" "-n" "multiline") (= "true" v)
    v))

(defn- unquote-string [s]
  (if (and (string? s)
           (<= 2 (count s))
           (str/starts-with? s "\"")
           (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- table-rows [table]
  (mapv #(zipmap (:headers table) %) (:rows table)))

(defn- ensure-parent! [path]
  (when-let [parent (.getParentFile (io/file path))]
    (.mkdirs parent)))

(defn- generated-content [{:strs [content lines]}]
  (cond
    lines   (str/join "\n" (map #(str "line " %) (range 1 (inc (parse-long lines)))))
    content (str/replace content "\\n" "\n")
    :else   ""))

(defn- strip-quotes [s]
  (if (and (string? s) (str/starts-with? s "\"") (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- tool-default-var [tool-name key]
  (case [(strip-quotes tool-name) key]
    ["glob" "head_limit"] #'glob/*default-head-limit*
    ["read" "limit"] #'builtin/*default-read-limit*
    ["grep" "head_limit"] #'builtin/*default-grep-head-limit*
    nil))

(defn- with-tool-defaults [f]
  (if-let [bindings (seq (g/get :tool-default-bindings))]
    (with-bindings (into {} bindings) (f))
    (f)))

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
    (ensure-parent! path)
    (spit path lines)))

(defgiven files-exist "the following files exist:"
  [table]
  (doseq [row (table-rows table)]
    (let [path (resolve-path (get row "name"))]
      (ensure-parent! path)
      (spit path (generated-content row))
      (when-let [mtime (get row "mtime")]
        (.setLastModified (io/file path)
                          (.toEpochMilli (java.time.Instant/parse mtime)))))))

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

(defgiven default-tool-value-is "the default {string} {word} is {n:int}"
  [tool-name key n]
  (if-let [var (tool-default-var (unquote-string tool-name) key)]
    (g/update! :tool-default-bindings #(assoc (or % {}) var n))
    (throw (ex-info "unknown tool default" {:tool tool-name :key key}))))

;; endregion ^^^^^ File / Directory Setup ^^^^^

;; region ----- Tool Execution -----

(defwhen tool-executed "tool {name:string} is executed with:"
  [name table]
  (let [all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]] [(keyword k) v]) all-rows))
        args     (cond-> args
                   (state-dir) (assoc :state-dir (state-dir)))
        result   (with-tool-defaults #(registry/execute name args))]
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
        args     (cond-> args
                   (state-dir) (assoc :state-dir (state-dir)))
        result   (with-tool-defaults #(registry/execute tool-name args))]
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

(defthen tool-result-lines-match "the tool result lines match:"
  [table]
  (let [needles (mapv #(or (get % "text") (first (vals %))) (table-rows table))
        lines   (vec (result-lines))]
    (loop [needles needles
           from    0]
      (when-let [needle (first needles)]
        (let [idx (first (keep-indexed (fn [i line]
                                         (when (and (<= from i) (str/includes? line needle)) i))
                                       lines))]
          (g/should (some? idx))
          (recur (rest needles) idx))))))

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
