(ns isaac.features.steps.tools
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.loader :as config]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.glob :as glob]
    [isaac.tool.memory :as memory]
    [isaac.tool.web-fetch :as web-fetch]
    [isaac.tool.registry :as registry]
    [speclj.core :refer [pending]]))

;; region ----- Helpers -----

(defn- state-dir [] (g/get :state-dir))

(defn- with-feature-fs [f]
  (if-let [mem (g/get :mem-fs)]
    (binding [isaac.fs/*fs* mem]
      (f))
    (f)))

(defn- resolve-path [p]
  (if (str/starts-with? p "/")
    p
    (let [root     (state-dir)
          root-name (.getName (io/file root))]
      (if (str/starts-with? p (str root-name "/"))
        (str root "/" (subs p (inc (count root-name))))
        (str root "/" p)))))

(defn- result-text []
  (let [r (g/get :tool-result)]
    (or (:result r) (:error r) "")))

(defn- result-lines []
  (str/split-lines (result-text)))

(defn- parse-tool-value [k v]
  (cond
    (and (string? v) (or (str/starts-with? v "[") (str/starts-with? v "{")))
    (try (edn/read-string v) (catch Exception _ v))

    :else
    (case k
      ("filePath" "path" "workdir") (resolve-path v)
      ("offset" "limit" "timeout" "head_limit" "num_results" "-A" "-B" "-C") (parse-long v)
      ("replaceAll" "-i" "-n" "multiline") (= "true" v)
      v)))

(defn- with-current-time [f]
  (if-let [current-time (g/get :current-time)]
    (binding [memory/*now* current-time]
      (f))
    (f)))

(defn- unquote-string [s]
  (if (and (string? s)
           (<= 2 (count s))
           (str/starts-with? s "\"")
           (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- table-rows [table]
  (mapv #(zipmap (:headers table) %) (:rows table)))

(defn- kv-rows [table]
  (cond-> (:rows table)
    (seq (:headers table)) (conj (:headers table))))

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
    ["web_fetch" "limit"] #'web-fetch/*default-limit*
    nil))

(defn- with-tool-defaults [f]
  (if-let [bindings (seq (g/get :tool-default-bindings))]
    (with-bindings (into {} bindings) (f))
    (f)))

(defn- query-param [url name]
  (when-let [value (some-> (re-find (re-pattern (str "(?:^|[?&])" name "=([^&]+)")) url)
                           second)]
    (java.net.URLDecoder/decode value "UTF-8")))

(defn- merge-url-stub [url f]
  (g/update! :url-stubs
             (fn [stubs]
               (let [stubs (or stubs {})
                     stub  (merge {:status 200 :headers {} :body ""} (get stubs url {}))]
                 (assoc stubs url (f stub))))))

(defn- search-response [query]
  (when-let [results (get (g/get :search-results) query)]
    {:status  200
     :headers {"content-type" "application/json"}
     :body    (json/generate-string {:web {:results results}})}))

(defn- with-tool-config [f]
  (if-let [search-config (g/get :web-search-config)]
    (with-redefs [config/load-config (fn [& _] {:tools {:web_search search-config}})]
      (f))
    (f)))

(defn- with-http-stubs [f]
  (let [stubs          (g/get :url-stubs)
        search-results (g/get :search-results)]
    (if (or stubs search-results)
      (with-redefs [http/get (fn [url _opts]
                               (or (get stubs url)
                                   (search-response (query-param url "q"))
                                   (throw (ex-info "no stubbed response for URL" {:url url}))))]
        (f))
      (f))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Registration -----

(defgiven builtin-tools-registered "the built-in tools are registered"
  "Clears the tool registry, then registers every built-in tool (read,
   write, edit, exec, grep, glob, web_fetch, web_search, memory_*).
   Required for features that execute tools — most other features
   should skip this unless they actually need to run tools."
  []
  (registry/clear!)
  (builtin/register-all! registry/register!))

(defgiven nil-tool-registered #"a tool \"([^\"]+)\" that returns nil is registered"
  [name]
  (registry/register! {:name name :description "Returns nil" :handler (fn [_] nil)}))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- File / Directory Setup -----

(defgiven clean-test-dir "a clean test directory {dir:string}"
  "Wipes the directory on the REAL filesystem and recreates it, then
   binds :state-dir. Use for tool tests that need real files (exec,
   glob with mtimes, etc.) — not compatible with mem-fs."
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
    (with-feature-fs
      #(do
         (isaac.fs/mkdirs (isaac.fs/parent path))
         (isaac.fs/spit path actual)))))

(defgiven file-with-lines #"a file \"([^\"]+)\" exists with (\d+) lines"
  [name n]
  (let [path  (resolve-path name)
        lines (str/join "\n" (map #(str "line " %) (range 1 (inc (parse-long n)))))]
    (with-feature-fs
      #(do
         (isaac.fs/mkdirs (isaac.fs/parent path))
         (isaac.fs/spit path lines)))))

(defgiven files-exist "the following files exist:"
  [table]
  (doseq [row (table-rows table)]
    (let [path (resolve-path (get row "name"))]
      (with-feature-fs
        #(do
           (isaac.fs/mkdirs (isaac.fs/parent path))
           (isaac.fs/spit path (generated-content row))))
      (when (and (nil? (g/get :mem-fs)) (get row "mtime"))
        (.setLastModified (io/file path)
                          (.toEpochMilli (java.time.Instant/parse (get row "mtime"))))))))

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

(defgiven url-responds-with "the URL {string} responds with:"
  "Registers an HTTP stub for the URL. Table rows configure the stubbed
   response: 'status' sets HTTP status; 'header.<name>' sets a header.
   Pair with 'the URL X has body:' to set the body. Applies to web_fetch
   / web_search and any other HTTP-making tool."
  [url table]
  (doseq [[path value] (map (juxt first second) (kv-rows table))]
    (merge-url-stub (unquote-string url)
                    (fn [stub]
                      (cond
                        (= "status" path)
                        (assoc stub :status (parse-long value))

                        (str/starts-with? path "header.")
                        (assoc-in stub [:headers (str/lower-case (subs path 7))] value)

                        :else
                        stub)))))

(defgiven url-has-body "the URL {string} has body:"
  [url body]
  (merge-url-stub (unquote-string url)
                  (fn [stub]
                    (-> stub
                        (assoc :body (str/trim body))
                        (update :headers #(if (contains? % "content-type")
                                            %
                                            (assoc % "content-type" "text/html")))))))

(defgiven search-query-returns-results #"the search query \"([^\"]+)\" returns results:"
  [query table]
  (g/assoc! :web-search-config {:provider :brave :api-key "test-brave-api-key"})
  (g/update! :search-results assoc query
             (mapv (fn [row]
                     {:title       (get row "title")
                      :url         (get row "url")
                      :description (get row "description")})
                   (table-rows table))))

(defgiven brave-api-key-is-set "the BRAVE_API_KEY environment variable is set"
  []
  (if-let [api-key (System/getenv "BRAVE_API_KEY")]
    (g/assoc! :web-search-config {:provider :brave :api-key api-key})
    (pending "BRAVE_API_KEY is not set; skipping live web_search integration scenario")))

(defgiven current-time-is "the current time is {iso:string}"
  "Sets :current-time. The tool execution harness binds this as
   memory/*now* so memory_write etc. use the virtual time instead of
   the real clock."
  [iso]
  (g/assoc! :current-time (java.time.Instant/parse iso)))

;; endregion ^^^^^ File / Directory Setup ^^^^^

;; region ----- Tool Execution -----

(defn- execute-tool* [name args]
  (with-tool-defaults
    (fn []
      (with-tool-config
        (fn []
          (with-http-stubs
            (fn []
              (with-feature-fs
                (fn []
                  (with-current-time
                    (fn []
                      (registry/execute name args))))))))))))

(defwhen tool-executed "tool {name:string} is executed with:"
  "Invokes the registered tool with args taken from the table (column
   headers become keyword keys). Wraps execution in the tool-defaults,
   tool-config, http-stub, feature-fs, and current-time bindings.
   Stores raw result in :tool-result."
  [name table]
  (let [all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]] [(keyword k) v]) all-rows))
        args     (cond-> args
                   (state-dir) (assoc :state-dir (state-dir)))
        result   (execute-tool* name args)]
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
        result   (execute-tool* tool-name args)]
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
  (let [lines     (vec (result-lines))
        headers   (:headers table)
        row-maps  (table-rows table)
        has-index? (contains? (set headers) "#index")
        resolve-index (fn [idx]
                        (let [resolved (if (neg? idx) (+ (count lines) idx) idx)]
                          (when (<= 0 resolved (dec (count lines)))
                            resolved)))]
    (if has-index?
      (doseq [row row-maps]
        (let [needle      (or (get row "text") (first (vals row)))
              idx         (parse-long (get row "#index"))
              resolved    (resolve-index idx)
              line        (when (some? resolved) (nth lines resolved nil))]
          (g/should (some? resolved))
          (g/should (str/includes? (or line "") needle))))
      (let [needles (mapv #(or (get % "text") (first (vals %))) row-maps)]
        (loop [needles needles
               from    0]
          (when-let [needle (first needles)]
            (let [idx (first (keep-indexed (fn [i line]
                                             (when (and (<= from i) (str/includes? line needle)) i))
                                           lines))]
              (g/should (some? idx))
              (recur (rest needles) idx))))))))

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
        actual (with-feature-fs #(isaac.fs/slurp path))
        expect (str/replace content "\\n" "\n")]
    (g/should= expect actual)))

(defthen file-matches "the file {name:string} matches:"
  [name table]
  (let [path    (resolve-path name)
        needles (mapv #(or (get % "text") (first (vals %))) (table-rows table))
        lines   (vec (str/split-lines (or (with-feature-fs #(isaac.fs/slurp path)) "")))]
    (loop [needles needles
           from    0]
      (when-let [needle (first needles)]
        (let [idx (first (keep-indexed (fn [i line]
                                         (when (and (<= from i) (str/includes? line needle)) i))
                                       lines))]
          (g/should (some? idx))
          (recur (rest needles) idx))))))

;; endregion ^^^^^ Assertions ^^^^^
