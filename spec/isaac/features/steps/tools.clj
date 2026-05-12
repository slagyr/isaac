(ns isaac.features.steps.tools
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen helper!]]
    [isaac.config.loader :as config]
    [isaac.config.schema :as schema]
    [isaac.features.matchers :as match]
    [isaac.system :as system]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.file :as file]
    [isaac.tool.glob :as glob]
    [isaac.tool.grep :as grep]
    [isaac.tool.memory :as memory]
    [isaac.tool.web-fetch :as web-fetch]
    [isaac.tool.registry :as registry]
    [isaac.tool.web-search :as web-search]
    [speclj.core :refer [pending]]))

(helper! isaac.features.steps.tools)

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
      ("file_path" "path" "workdir") (resolve-path v)
      ("offset" "limit" "timeout" "head_limit" "num_results" "-A" "-B" "-C") (parse-long v)
      ("replace_all" "-i" "-n" "multiline" "reset") (= "true" v)
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
    ["read" "limit"] #'file/*default-read-limit*
    ["grep" "head_limit"] #'grep/*default-head-limit*
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

(defn builtin-tools-registered []
  (registry/clear!)
  (builtin/register-all!))

(defn provider-registered-for-tool [provider tool]
  (schema/register-schema! :tool tool {:provider {:type :keyword}})
  (schema/register-schema! :tool-provider {:tool tool :provider provider} {}))

(defn provider-registered-for-tool-with-schema [provider tool schema-str]
  (let [fields (edn/read-string schema-str)]
    (schema/register-schema! :tool tool {:provider {:type :keyword}})
    (schema/register-schema! :tool-provider {:tool tool :provider provider} fields)))

(defn web-search-initialized [_tool]
  (builtin/register-all! #{"web_search"})
  (web-search/register-schemas!))

(defn nil-tool-registered [name]
  (registry/register! {:name name :description "Returns nil" :handler (fn [_] nil)}))

;; endregion ^^^^^ Registration ^^^^^

;; region ----- File / Directory Setup -----

(defn clean-test-dir [dir]
  (let [abs-dir (if (str/starts-with? dir "/")
                  dir
                  (str (System/getProperty "user.dir") "/" dir))
        f       (io/file abs-dir)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))
    (.mkdirs f)
    (system/register! :state-dir abs-dir)
    (g/assoc! :state-dir abs-dir)))

(defn file-with-content [name content]
  (let [path   (resolve-path name)
        actual (str/replace content "\\n" "\n")]
    (with-feature-fs
      #(do
         (isaac.fs/mkdirs (isaac.fs/parent path))
         (isaac.fs/spit path actual)))))

(defn file-with-lines [name n]
  (let [path  (resolve-path name)
        lines (str/join "\n" (map #(str "line " %) (range 1 (inc (parse-long n)))))]
    (with-feature-fs
      #(do
         (isaac.fs/mkdirs (isaac.fs/parent path))
         (isaac.fs/spit path lines)))))

(defn files-exist [table]
  (doseq [row (table-rows table)]
    (let [path (resolve-path (get row "name"))]
      (with-feature-fs
        #(do
           (isaac.fs/mkdirs (isaac.fs/parent path))
           (isaac.fs/spit path (generated-content row))))
      (when (and (nil? (g/get :mem-fs)) (get row "mtime"))
        (.setLastModified (io/file path)
                          (.toEpochMilli (java.time.Instant/parse (get row "mtime"))))))))

(defn binary-file-exists [name]
  (let [path  (resolve-path name)
        bytes (byte-array (map unchecked-byte
                               [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
                                0x00 0x00 0x00 0x0D 0x49 0x48 0x44 0x52]))]
    (.mkdirs (.getParentFile (io/file path)))
    (with-open [out (io/output-stream (io/file path))]
      (.write out bytes))))

(defn dir-with-files [dir-name files-str]
  (let [dir-path   (resolve-path dir-name)
        _          (.mkdirs (io/file dir-path))
        file-names (mapv second (re-seq #"\"([^\"]+)\"" files-str))]
    (doseq [f file-names]
      (spit (str dir-path "/" f) ""))))

(defn exec-timeout [n]
  (g/assoc! :exec-timeout n))

(defn default-tool-value-is [tool-name key n]
  (if-let [var (tool-default-var (unquote-string tool-name) key)]
    (g/update! :tool-default-bindings #(assoc (or % {}) var n))
    (throw (ex-info "unknown tool default" {:tool tool-name :key key}))))

(defn url-responds-with [url table]
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

(defn url-has-body [url body]
  (merge-url-stub (unquote-string url)
                  (fn [stub]
                    (-> stub
                        (assoc :body (str/trim body))
                        (update :headers #(if (contains? % "content-type")
                                            %
                                            (assoc % "content-type" "text/html")))))))

(defn search-query-returns-results [query table]
  (g/assoc! :web-search-config {:provider :brave :api-key "test-brave-api-key"})
  (g/update! :search-results assoc query
             (mapv (fn [row]
                     {:title       (get row "title")
                      :url         (get row "url")
                      :description (get row "description")})
                   (table-rows table))))

(defn brave-api-key-is-set []
  (if-let [api-key (System/getenv "BRAVE_API_KEY")]
    (g/assoc! :web-search-config {:provider :brave :api-key api-key})
    (pending "BRAVE_API_KEY is not set; skipping live web_search integration scenario")))

(defn current-time-is [iso]
  (g/assoc! :current-time (java.time.Instant/parse iso)))

(defn current-session-is [key]
  (g/assoc! :current-session-key key))

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

(defn- base-tool-args []
  (cond-> {}
    (state-dir)                  (assoc "state_dir" (state-dir))
    (g/get :current-session-key) (assoc "session_key" (g/get :current-session-key))))

(defn tool-executed [name table]
  (let [all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} all-rows)
        args     (merge (base-tool-args) args)
        result   (execute-tool* name args)]
    (g/assoc! :tool-result result)))

(defn tool-called [tool-name table]
  (registry/clear!)
  (builtin/register-all!)
  (let [timeout  (g/get :exec-timeout)
        all-rows (cond-> (:rows table)
                   (seq (:headers table)) (conj (:headers table)))
        args     (into {} (map (fn [[k v]]
                                 [k (parse-tool-value k v)])
                                all-rows))
        args     (if timeout (assoc args "timeout" timeout) args)
        args     (merge (base-tool-args) args)
        result   (execute-tool* tool-name args)]
    (g/assoc! :tool-result result)))

(defn tool-called-no-args [tool-name]
  (registry/clear!)
  (builtin/register-all!)
  (let [result (execute-tool* tool-name (base-tool-args))]
    (g/assoc! :tool-result result)))

;; endregion ^^^^^ Tool Execution ^^^^^

;; region ----- Assertions -----

(defn tool-result-contains [text]
  (g/should (str/includes? (result-text) text)))

(defn tool-result-lines-match [table]
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

(defn tool-result-not-contains [text]
  (g/should-not (str/includes? (result-text) text)))

(defn tool-result-not-error []
  (g/should-not (:isError (g/get :tool-result))))

(defn tool-result-json-has [table]
  (let [result (g/get :tool-result)
        parsed (json/parse-string (or (:result result) "{}") true)
        r      (match/match-object table parsed)]
    (g/should= [] (:failures r))))

(defn tool-result-is-error []
  (g/should (:isError (g/get :tool-result))))

(defn tool-result-indicates-error []
  (g/should (:isError (g/get :tool-result))))

(defn file-has-content [name content]
  (let [path   (resolve-path name)
        actual (with-feature-fs #(isaac.fs/slurp path))
        expect (str/replace content "\\n" "\n")]
    (g/should= expect actual)))

(defn file-matches [name table]
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

;; region ----- Routing -----

(defgiven "the built-in tools are registered" tools/builtin-tools-registered
  "Clears the tool registry, then registers every built-in tool (read,
   write, edit, exec, grep, glob, web_fetch, web_search, memory_*).
   Required for features that execute tools — most other features
   should skip this unless they actually need to run tools.")

(defgiven #"a tool \"([^\"]+)\" that returns nil is registered" tools/nil-tool-registered)

(defgiven "a clean test directory {dir:string}" tools/clean-test-dir
  "Wipes the directory on the REAL filesystem and recreates it, then
   binds :state-dir. Use for tool tests that need real files (exec,
   glob with mtimes, etc.) — not compatible with mem-fs.")

(defgiven "a file {name:string} exists with content {content:string}" tools/file-with-content)

(defgiven #"a file \"([^\"]+)\" exists with (\d+) lines" tools/file-with-lines)

(defgiven "the following files exist:" tools/files-exist)

(defgiven "a binary file {name:string} exists" tools/binary-file-exists)

(defgiven #"a directory \"([^\"]+)\" exists with files (.+)" tools/dir-with-files)

(defgiven "the exec timeout is set to {n:int} milliseconds" tools/exec-timeout)

(defgiven "the default {string} {word} is {n:int}" tools/default-tool-value-is)

(defgiven "the URL {string} responds with:" tools/url-responds-with
  "Registers an HTTP stub for the URL. Table rows configure the stubbed
   response: 'status' sets HTTP status; 'header.<name>' sets a header.
   Pair with 'the URL X has body:' to set the body. Applies to web_fetch
   / web_search and any other HTTP-making tool.")

(defgiven "the URL {string} has body:" tools/url-has-body)

(defgiven #"the search query \"([^\"]+)\" returns results:" tools/search-query-returns-results)

(defgiven "the BRAVE_API_KEY environment variable is set" tools/brave-api-key-is-set)

(defgiven "the current session is {key:string}" tools/current-session-is)

(defgiven "the current time is {iso:string}" tools/current-time-is
  "Sets :current-time. The tool execution harness binds this as
   memory/*now* so memory_write etc. use the virtual time instead of
   the real clock.")

(defwhen "tool {name:string} is executed with:" tools/tool-executed
  "Invokes the registered tool with args taken from the table (column
   headers become string keys). Wraps execution in the tool-defaults,
   tool-config, http-stub, feature-fs, and current-time bindings.
   Stores raw result in :tool-result.")

(defwhen "the tool {name:string} is called with:" tools/tool-called)

(defwhen "the tool {name:string} is called" tools/tool-called-no-args)

(defthen "the tool result contains {text:string}" tools/tool-result-contains)

(defthen "the tool result lines match:" tools/tool-result-lines-match)

(defthen "the tool result does not contain {text:string}" tools/tool-result-not-contains)

(defthen "the tool result is not an error" tools/tool-result-not-error)

(defthen "the tool result JSON has:" tools/tool-result-json-has)

(defthen "the tool result is an error" tools/tool-result-is-error)

(defthen "the tool result should indicate an error" tools/tool-result-indicates-error)

(defthen "the file {name:string} has content {content:string}" tools/file-has-content)

(defthen "the file {name:string} matches:" tools/file-matches)

(defgiven "the {provider:string} provider is registered for {tool:string}" tools/provider-registered-for-tool)

(defgiven "a {provider:string} provider is registered for {tool:string} with schema:" tools/provider-registered-for-tool-with-schema)

(defwhen "the {tool:string} tool is initialized" tools/web-search-initialized)

;; endregion ^^^^^ Routing ^^^^^
