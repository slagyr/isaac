(ns isaac.tool.builtin
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.tool.glob :as glob]
    [isaac.tool.web-fetch :as web-fetch]
    [isaac.tool.web-search :as web-search]
    [isaac.session.bridge :as bridge]
    [isaac.session.storage :as storage]
    [isaac.util.shell :as shell])
  (:import
    [java.io File]
    [java.net URI]
    [java.nio.file FileSystems Files LinkOption]
    [java.util.concurrent TimeUnit]))

;; region ----- Filesystem Boundaries -----

(defn- canonical-path [path]
  (.getCanonicalPath (io/file path)))

(defn- path-inside? [parent child]
  (let [parent (canonical-path parent)
        child  (canonical-path child)]
    (or (= parent child)
        (str/starts-with? child (str parent File/separator)))))

(defn- state-dir->home [state-dir]
  (if (= ".isaac" (.getName (io/file state-dir)))
    (.getParent (io/file state-dir))
    state-dir))

(defn- config-directories [state-dir]
  (set [(str state-dir "/config")
        (str state-dir "/.isaac/config")]))

(defn- crew-quarters [state-dir crew-id]
  (str state-dir "/crew/" crew-id))

(defn- allowed-directories [{:keys [session-key state-dir]}]
  (when (and session-key state-dir)
    (when-let [session (storage/get-session state-dir session-key)]
      (let [crew-id      (or (:crew session) (:agent session) "main")
            quarters     (crew-quarters state-dir crew-id)
            _            (fs/mkdirs quarters)
            cfg          (config/load-config {:home (state-dir->home state-dir)})
            directories  (or (get-in cfg [:crew crew-id :tools :directories]) [])]
        (vec (concat [quarters]
                     (keep (fn [directory]
                             (cond
                               (= :cwd directory) (:cwd session)
                               (= "cwd" directory) (:cwd session)
                               (string? directory) directory
                               :else nil))
                           directories)))))))

(defn- path-outside-error [file-path]
  {:isError true :error (str "path outside allowed directories: " file-path)})

(defn- ensure-path-allowed [args file-path]
  (when-let [directories (seq (allowed-directories args))]
    (let [denied-config? (some #(path-inside? % file-path) (config-directories (:state-dir args)))]
      (when (or denied-config?
                (not-any? #(path-inside? % file-path) directories))
        (path-outside-error file-path)))))

;; endregion ^^^^^ Filesystem Boundaries ^^^^^

;; region ----- read -----

(def ^:dynamic *default-read-limit* 2000)
(def ^:private binary-check-window 8192)

(defn- binary-content? [^String content]
  (let [len (min (count content) binary-check-window)]
    (loop [i 0]
      (cond
        (>= i len)                     false
        (= \u0000 (.charAt content i)) true
        :else                          (recur (inc i))))))

(defn- format-file-content [filePath content offset limit]
  (cond
    (binary-content? content)
    {:isError true :error (str "binary file: " filePath)}

    (= "" content)
    {:result "<empty file>"}

    :else
    (let [all-lines (str/split-lines content)
          total     (count all-lines)
          start     (if offset (max 0 (dec offset)) 0)
          effective (or limit *default-read-limit*)
          end       (min total (+ start effective))
          selected  (subvec (vec all-lines) start end)
          numbered  (map-indexed
                      (fn [i line] (str (+ start i 1) ": " line))
                      selected)
          lines     (cond-> (vec numbered)
                      (< end total)
                      (conj (str "... (truncated: showing " (count selected)
                                 " of " total " lines)")))]
      {:result (str/join "\n" lines)})))

(defn read-tool
  "Read file contents or list a directory.
   Args: {:filePath str :offset int :limit int}"
  [{:keys [filePath offset limit] :as args}]
  (or (ensure-path-allowed args filePath)
      (cond
        (not (fs/exists? filePath))
        {:isError true :error (str "not found: " filePath)}

        (when-let [entries (fs/children filePath)]
          (seq entries))
        {:result (str/join "\n" (sort (fs/children filePath)))}

        :else
        (format-file-content filePath (or (fs/slurp filePath) "") offset limit))))

;; endregion ^^^^^ read ^^^^^

;; region ----- write -----

(defn write-tool
  "Write content to a file, creating parent directories as needed.
   Args: {:filePath str :content str}"
  [{:keys [filePath content] :as args}]
  (or (ensure-path-allowed args filePath)
      (try
        (fs/mkdirs (fs/parent filePath))
        (fs/spit filePath content)
        {:result (str "wrote " filePath)}
        (catch Exception e
          {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ write ^^^^^

;; region ----- edit -----

(defn edit-tool
  "Replace text in a file.
   Args: {:filePath str :oldString str :newString str :replaceAll bool}"
  [{:keys [filePath oldString newString replaceAll] :as args}]
  (or (ensure-path-allowed args filePath)
      (if-not (fs/exists? filePath)
        {:isError true :error (str "not found: " filePath)}
        (let [content (or (fs/slurp filePath) "")
              count   (count (re-seq (java.util.regex.Pattern/compile
                                       (java.util.regex.Pattern/quote oldString))
                                     content))]
          (cond
            (= 0 count)
            {:isError true :error (str "not found: " oldString)}

            (and (> count 1) (not replaceAll))
            {:isError true :error (str "multiple matches for: " oldString)}

            :else
            (let [new-content (str/replace content oldString newString)]
              (fs/spit filePath new-content)
              {:result (str "edited " filePath)}))))))

;; endregion ^^^^^ edit ^^^^^

;; region ----- grep -----

(def ^:dynamic *default-grep-head-limit* 250)

(def ^:private grep-type->globs
  {"clj"  ["*.clj" "*.cljc" "*.cljs"]
   "edn"  ["*.edn"]
   "java" ["*.java"]
   "js"   ["*.js" "*.jsx"]
   "json" ["*.json"]
   "md"   ["*.md"]
   "py"   ["*.py"]
   "ts"   ["*.ts" "*.tsx"]
   "yaml" ["*.yaml" "*.yml"]
   "yml"  ["*.yml" "*.yaml"]})

(defn- string-key-map [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v]) m)))

(defn- grep-bool [args k default]
  (let [value (get args k)]
    (cond
      (nil? value)     default
      (boolean? value) value
      (string? value)  (= "true" (str/lower-case value))
      :else            (boolean value))))

(defn- grep-int [args k default]
  (let [value (get args k)]
    (cond
      (nil? value)     default
      (integer? value) value
      (string? value)  (parse-long value)
      :else            default)))

(defn- grep-globs [args]
  (concat
    (when-let [glob (get args "glob")]
      [glob])
    (get grep-type->globs (get args "type") [])))

(defn- grep-command [args]
  (let [mode          (or (get args "output_mode") "content")
        line-numbers? (grep-bool args "-n" (= mode "content"))
        command       (cond-> ["rg" "--color=never" "--with-filename"]
                        (and (= mode "content") line-numbers?) (conj "-n")
                        (grep-bool args "-i" false)            (conj "-i")
                        (grep-int args "-A" nil)               (conj "-A" (str (grep-int args "-A" nil)))
                        (grep-int args "-B" nil)               (conj "-B" (str (grep-int args "-B" nil)))
                        (grep-int args "-C" nil)               (conj "-C" (str (grep-int args "-C" nil)))
                        (grep-bool args "multiline" false)     (conj "--multiline")
                        (= mode "files_with_matches")          (conj "-l")
                        (= mode "count")                       (conj "-c"))]
    (-> command
        (into (mapcat (fn [glob] ["-g" glob]) (grep-globs args)))
        (conj (get args "pattern"))
        (conj (get args "path")))))

(defn- grep-result [output offset head-limit]
  (let [lines       (cond->> (str/split-lines output)
                      (pos? offset) (drop offset))
        truncated?  (and (pos? head-limit) (> (count lines) head-limit))
        shown-lines (if (pos? head-limit) (take head-limit lines) lines)
        shown-lines (cond-> (vec shown-lines)
                     truncated? (conj "Results truncated."))]
    {:result (str/join "\n" shown-lines)}))

(defn grep-tool
  "Search file contents with ripgrep.
   Args: {:pattern str :path str :glob str :type str :-i bool :-n bool :-A int :-B int :-C int
          :multiline bool :output_mode str :head_limit int :offset int}"
  [{:keys [path] :as args}]
  (or (ensure-path-allowed args path)
      (if-not (shell/cmd-available? "rg")
        {:isError true :error "rg not found on PATH"}
        (let [args       (string-key-map args)
              offset     (or (grep-int args "offset" 0) 0)
              head-limit (or (grep-int args "head_limit" *default-grep-head-limit*) *default-grep-head-limit*)
              {:keys [exit out err]} (apply sh/sh (grep-command args))]
          (cond
            (and (= 1 exit) (str/blank? out) (str/blank? err))
            {:result "no matches"}

            (zero? exit)
            (grep-result out offset head-limit)

            :else
            {:isError true
             :error   (str/trim (or (not-empty err)
                                    (not-empty out)
                                    (str "rg failed with exit " exit)))})))))

;; endregion ^^^^^ grep ^^^^^

;; region ----- glob -----

(defn- glob-root [{:keys [path session-key state-dir]}]
  (or path
      (when (and session-key state-dir)
        (or (:cwd (storage/get-session state-dir session-key))
            state-dir))
      state-dir
      (System/getProperty "user.dir")))

(defn- normalize-relative-path [path]
  (str/replace (.toString path) File/separator "/"))

(defn- glob-candidates [root-path]
  (if (Files/isRegularFile root-path (make-array LinkOption 0))
    [root-path]
    (with-open [stream (Files/walk root-path (into-array java.nio.file.FileVisitOption []))]
      (->> (iterator-seq (.iterator stream))
           (filter #(Files/isRegularFile % (make-array LinkOption 0)))
           vec))))

(defn- glob-matches [root pattern]
  (let [root-path (-> root io/file .toPath)
        matcher   (.getPathMatcher (FileSystems/getDefault) (str "glob:" pattern))]
    (->> (glob-candidates root-path)
         (map (fn [path]
                (let [relative (if (= path root-path)
                                 (.getFileName path)
                                 (.relativize root-path path))]
                  {:path    path
                   :display (normalize-relative-path relative)
                   :mtime   (.toMillis (Files/getLastModifiedTime path (make-array LinkOption 0)))})))
         (filter #(when-let [display (:display %)]
                    (.matches matcher (.getPath (FileSystems/getDefault) display (make-array String 0)))))
         (sort-by (juxt (comp - :mtime) :display)))))

(defn- glob-result [matches head-limit]
  (let [total       (count matches)
        truncated?  (and (pos? head-limit) (> total head-limit))
        shown       (if (pos? head-limit) (take head-limit matches) matches)
        lines       (mapv :display shown)
        lines       (cond-> lines
                      truncated? (conj (str "Results truncated. " total " total matches.")))]
    {:result (str/join "\n" lines)}))

(defn glob-tool
  "List files matching a shell-style glob pattern.
   Args: {:pattern str :path str :head_limit int}"
  [{:keys [pattern head_limit] :as args}]
  (let [root (glob-root args)]
    (or (ensure-path-allowed args root)
        (let [matches (glob-matches root pattern)]
          (if (seq matches)
            (glob-result matches (or head_limit glob/*default-head-limit*))
            {:result "no matches"})))))

;; endregion ^^^^^ glob ^^^^^

;; region ----- web_fetch -----

(def ^:private default-web-timeout 30000)
(def ^:private max-web-redirects 5)

(defn- web-header [headers name]
  (or (get headers name)
      (get headers (str/lower-case name))
      (get headers (str/capitalize name))))

(defn- allowed-content-type? [content-type]
  (let [content-type (some-> content-type str/lower-case)]
    (or (nil? content-type)
        (str/starts-with? content-type "text/")
        (contains? #{"application/json" "application/xml" "application/xhtml+xml"} content-type))))

(defn- redirect? [status]
  (contains? #{301 302 303 307 308} status))

(defn- absolute-location [url location]
  (str (.resolve (URI. url) location)))

(defn- http-get! [url timeout]
  (http/get url {:timeout          timeout
                 :throw            false
                 :follow-redirects false}))

(defn- fetch-response [url timeout redirects-left]
  (let [response (http-get! url timeout)
        status   (:status response)
        location (web-header (:headers response) "location")]
    (cond
      (and (redirect? status) location (pos? redirects-left))
      (recur (absolute-location url location) timeout (dec redirects-left))

      (and (redirect? status) location)
      {:isError true :error "too many redirects"}

      :else
      response)))

(defn- strip-html [body]
  (-> body
      (str/replace #"(?is)<!--.*?-->" " ")
      (str/replace #"(?is)<script\b[^>]*>.*?</script>" " ")
      (str/replace #"(?is)<style\b[^>]*>.*?</style>" " ")
      (str/replace #"(?is)<[^>]+>" "\n")
      (str/replace #"[ \t\x0B\f\r]+" " ")
      (str/split-lines)
      (->> (map str/trim)
           (remove str/blank?)
           (str/join "\n"))))

(defn- extract-body [body format]
  (if (= "raw" format)
    body
    (strip-html body)))

(defn- truncate-lines [text limit]
  (let [lines      (str/split-lines (or text ""))
        total      (count lines)
        truncated? (and (pos? limit) (> total limit))
        shown      (if (pos? limit) (take limit lines) lines)
        shown      (cond-> (vec shown)
                     truncated? (conj (str "Results truncated. " total " total lines.")))]
    (str/join "\n" shown)))

(defn web-fetch-tool
  "Fetch URL content via HTTP GET.
   Args: {:url str :format str :timeout int}"
  [{:keys [url format timeout]}]
  (if-not (re-matches #"https?://.+" (or url ""))
    {:isError true :error (str "unsupported URL: " url)}
    (let [response (fetch-response url (or timeout default-web-timeout) max-web-redirects)]
      (if (:isError response)
        response
        (let [status       (:status response)
              headers      (:headers response)
              content-type (web-header headers "content-type")
              content-type (some-> content-type (str/split #";") first)
              body         (str (:body response))]
          (cond
            (not (allowed-content-type? content-type))
            {:isError true :error (str "binary content-type: " content-type)}

            (>= status 400)
            {:isError true :error (str "HTTP " status) :status status}

            :else
            {:status status
             :result (truncate-lines (extract-body body (or format "text")) web-fetch/*default-limit*)}))))))

;; endregion ^^^^^ web_fetch ^^^^^

;; region ----- web_search -----

(def ^:private brave-search-endpoint "https://api.search.brave.com/res/v1/web/search")

(defn- web-search-config [state-dir]
  (get-in (config/load-config {:home (state-dir->home state-dir)}) [:tools :web_search]))

(defn- web-search-api-key [state-dir]
  (:api-key (web-search-config state-dir)))

(defn- web-search-provider [state-dir]
  (or (:provider (web-search-config state-dir)) :brave))

(defn- web-search-config-error []
  {:isError true
   :error   "web_search not configured: set :tools :web_search :api_key (e.g. ${BRAVE_API_KEY})"})

(defn- brave-search-url [query num-results]
  (str brave-search-endpoint
       "?q=" (java.net.URLEncoder/encode query "UTF-8")
       "&count=" num-results))

(defn- parse-search-body [body]
  (json/parse-string body true))

(defn- search-results [body]
  (get-in body [:web :results]))

(defn- format-search-result [idx {:keys [title url description]}]
  (str idx ". " title "\n"
       "   " url "\n"
       "   " description))

(defn- format-search-results [results]
  (->> results
       (map-indexed (fn [idx result] (format-search-result (inc idx) result)))
       (str/join "\n\n")))

(defn web-search-tool
  "Search the web via a configured provider.
   Args: {:query str :num_results int :state-dir str}"
  [{:keys [query num_results state-dir]}]
  (let [provider   (web-search-provider state-dir)
        api-key    (web-search-api-key state-dir)
        num-results (or num_results 5)]
    (cond
      (str/blank? api-key)
      (web-search-config-error)

      (not= :brave provider)
      {:isError true :error (str "unsupported web_search provider: " provider)}

      :else
      (try
        (let [response (http/get (brave-search-url query num-results)
                                 {:headers {"X-Subscription-Token" api-key}
                                  :throw   false
                                  :timeout 30000})
              status   (:status response)]
          (cond
            (>= status 400)
            {:isError true :error (str "HTTP " status)}

            :else
            (let [results (-> response :body parse-search-body search-results)]
              (if (seq results)
                {:result (format-search-results (take num-results results))}
                {:result "no results"}))))
        (catch Exception e
          {:isError true :error (.getMessage e)})))))

;; endregion ^^^^^ web_search ^^^^^

;; region ----- exec -----

(def ^:private default-timeout 30000)
(def ^:private poll-interval-ms 50)

(defn start-process [{:keys [command workdir]}]
  (let [pb (doto (ProcessBuilder. ["/bin/sh" "-c" command])
             (.redirectErrorStream true))]
    (when workdir
      (.directory pb (io/file workdir)))
    (.start pb)))

(defn process-finished? [proc timeout-ms]
  (.waitFor proc timeout-ms TimeUnit/MILLISECONDS))

(defn destroy-process! [proc]
  (.destroy proc)
  (when-not (process-finished? proc 100)
    (.destroyForcibly proc)))

(defn read-process-output [proc]
  (slurp (.getInputStream proc)))

(defn process-exit-value [proc]
  (.exitValue proc))

(defn exec-tool
  "Execute a shell command.
   Args: {:command str :workdir str :timeout int}"
  [{:keys [command workdir timeout session-key]}]
  (let [timeout-ms (or timeout default-timeout)]
    (try
      (let [proc (start-process {:command command :workdir workdir})]
        (loop [elapsed 0]
          (cond
            (bridge/cancelled? session-key)
            (do
              (destroy-process! proc)
              {:error :cancelled})

            (process-finished? proc poll-interval-ms)
            (let [output (read-process-output proc)
                  exit   (process-exit-value proc)]
              (if (zero? exit)
                {:result (str/trim output)}
                {:isError true :error (str "exit " exit ": " (str/trim output))}))

            (>= elapsed timeout-ms)
            (do
              (destroy-process! proc)
              {:isError true :error "timeout exceeded"})

            :else
            (recur (+ elapsed poll-interval-ms)))))
      (catch Exception e
        {:isError true :error (.getMessage e)}))))

;; endregion ^^^^^ exec ^^^^^

;; region ----- Registration -----

(defn register-all!
  "Register all built-in tools with the given registry.
   With 1-arity, registers every built-in tool.
   With 2-arity, registers only the tools in the allow list (nil registers none)."
  ([registry-ns]
   (register-all! registry-ns ::all))
  ([registry-ns allowed-tools]
   (let [normalized (when-not (= ::all allowed-tools)
                      (some->> allowed-tools
                               (map (fn [tool]
                                      (cond
                                        (keyword? tool) (name tool)
                                        (string? tool)  tool
                                        :else           (str tool))))
                               set))
         allow?     (fn [tool-name]
                      (or (= ::all allowed-tools)
                          (boolean (and normalized (contains? normalized tool-name)))))]
     (when (allow? "read")
       (registry-ns {:name        "read"
                     :description "Read file contents or list a directory"
                     :parameters  {:type       "object"
                                    :properties {:filePath {:type "string" :description "Path to file or directory"}
                                                 :offset   {:type "integer" :description "Start line (1-indexed)"}
                                                 :limit    {:type "integer" :description "Max lines to return"}}
                                    :required   ["filePath"]}
                     :handler     #'read-tool}))
     (when (allow? "write")
       (registry-ns {:name        "write"
                     :description "Write content to a file"
                     :parameters  {:type       "object"
                                    :properties {:filePath {:type "string" :description "Path to write"}
                                                 :content  {:type "string" :description "Content to write"}}
                                    :required   ["filePath" "content"]}
                     :handler     #'write-tool}))
      (when (allow? "edit")
        (registry-ns {:name        "edit"
                      :description "Replace text in a file"
                     :parameters  {:type       "object"
                                    :properties {:filePath   {:type "string" :description "File to edit"}
                                                 :oldString  {:type "string" :description "Text to replace"}
                                                 :newString  {:type "string" :description "Replacement text"}
                                                 :replaceAll {:type "boolean" :description "Replace all occurrences"}}
                                     :required   ["filePath" "oldString" "newString"]}
                      :handler     #'edit-tool}))
      (when (allow? "grep")
        (when-not (shell/cmd-available? "rg")
          (throw (ex-info "rg not found on PATH" {:tool "grep"})))
        (registry-ns {:name        "grep"
                      :description "Search file contents with ripgrep"
                      :parameters  {:type       "object"
                                     :properties {"pattern"     {:type "string" :description "Regex pattern to search for"}
                                                  "path"        {:type "string" :description "File or directory to search"}
                                                  "glob"        {:type "string" :description "Optional file glob filter"}
                                                  "type"        {:type "string" :description "Optional file type shorthand"}
                                                  "-i"          {:type "boolean" :description "Case-insensitive search"}
                                                  "-n"          {:type "boolean" :description "Include line numbers in content mode"}
                                                  "-A"          {:type "integer" :description "Context lines after each match"}
                                                  "-B"          {:type "integer" :description "Context lines before each match"}
                                                  "-C"          {:type "integer" :description "Context lines before and after each match"}
                                                  "multiline"   {:type "boolean" :description "Enable multiline matching"}
                                                  "output_mode" {:type "string" :description "content, files_with_matches, or count"}
                                                  "head_limit"  {:type "integer" :description "Maximum rows to return; 0 means unlimited"}
                                                  "offset"      {:type "integer" :description "Rows to skip before returning results"}}
                                     :required   ["pattern" "path"]}
                      :handler     #'grep-tool}))
      (when (allow? "glob")
        (registry-ns {:name        "glob"
                      :description "List files matching a glob pattern"
                      :parameters  {:type       "object"
                                     :properties {"pattern"    {:type "string" :description "Glob pattern to match"}
                                                  "path"       {:type "string" :description "Directory to search; defaults to cwd or state-dir"}
                                                  "head_limit" {:type "integer" :description "Maximum rows to return"}}
                                     :required   ["pattern"]}
                      :handler     #'glob-tool}))
      (when (allow? "web_fetch")
        (registry-ns {:name        "web_fetch"
                      :description "Fetch URL content via HTTP GET"
                      :parameters  {:type       "object"
                                     :properties {"url"     {:type "string" :description "HTTP or HTTPS URL to fetch"}
                                                  "format"  {:type "string" :description "text or raw"}
                                                  "timeout" {:type "integer" :description "Timeout in milliseconds"}}
                                     :required   ["url"]}
                      :handler     #'web-fetch-tool}))
      (when (allow? "web_search")
        (registry-ns {:name        "web_search"
                      :description "Search the web via Brave Search"
                      :parameters  {:type       "object"
                                     :properties {"query"       {:type "string" :description "Search query"}
                                                  "num_results" {:type "integer" :description "Maximum results to return"}}
                                     :required   ["query"]}
                      :handler     #'web-search-tool}))
      (when (allow? "exec")
        (registry-ns {:name        "exec"
                      :description "Execute a shell command"
                     :parameters  {:type       "object"
                                    :properties {:command {:type "string" :description "Command to run"}
                                                 :workdir {:type "string" :description "Working directory"}
                                                 :timeout {:type "integer" :description "Timeout in ms"}}
                                    :required   ["command"]}
                     :handler     #'exec-tool})))))

;; endregion ^^^^^ Registration ^^^^^
