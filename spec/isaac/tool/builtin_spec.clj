(ns isaac.tool.builtin-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.config.loader :as config]
    [isaac.bridge.cancellation :as bridge]
    [isaac.spec-helper :as helper]
    [isaac.tool.glob :as glob]
    [isaac.tool.builtin :as sut]
    [isaac.tool.registry :as registry]
    [isaac.tool.web-fetch :as web-fetch]
    [isaac.tool.web-search :as web-search]
    [isaac.logger :as log]
    [isaac.util.shell :as shell]
    [speclj.core :refer :all])
  (:import
    [java.io ByteArrayInputStream]))

(def test-dir (str (System/getProperty "user.dir") "/target/test-tools"))

(defn- clean! []
  (let [dir (io/file test-dir)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f))))
  (.mkdirs (io/file test-dir)))

(defn- write-file! [name content]
  (let [path (str test-dir "/" name)]
    (.mkdirs (.getParentFile (io/file path)))
    (spit path content)))

(defn- read-file [name]
  (slurp (str test-dir "/" name)))

(defn- set-mtime! [name iso-instant]
  (.setLastModified (io/file (str test-dir "/" name))
                    (.toEpochMilli (java.time.Instant/parse iso-instant))))

(describe "Built-in Tools"

  (before (clean!))
  (after (registry/clear!))

  ;; region ----- read -----

  (describe "read"

    (it "returns file contents with line-number prefixes"
      (write-file! "hello.txt" "Hello, world!")
      (let [result (sut/read-tool {"file_path" (str test-dir "/hello.txt")})]
        (should= "1: Hello, world!" (:result result))
        (should-be-nil (:isError result))))

    (it "returns multi-line file contents"
      (write-file! "multi.txt" "line one\nline two\nline three")
      (let [result (sut/read-tool {"file_path" (str test-dir "/multi.txt")})]
        (should (str/includes? (:result result) "line one"))
        (should (str/includes? (:result result) "line three"))))

    (it "returns error for missing file"
      (let [result (sut/read-tool {"file_path" (str test-dir "/no-such-file.txt")})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "lists directory contents"
      (.mkdirs (io/file (str test-dir "/mydir")))
      (write-file! "mydir/a.txt" "a")
      (write-file! "mydir/b.txt" "b")
      (let [result (sut/read-tool {"file_path" (str test-dir "/mydir")})]
        (should (str/includes? (:result result) "a.txt"))
        (should (str/includes? (:result result) "b.txt"))))

    (it "respects offset to skip leading lines"
      (write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {"file_path" (str test-dir "/numbered.txt") "offset" 10})]
        (should-not (str/includes? (:result result) "line 9"))
        (should (str/includes? (:result result) "line 10"))))

    (it "respects limit to cap returned lines"
      (write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {"file_path" (str test-dir "/numbered.txt") "offset" 10 "limit" 5})]
        (should (str/includes? (:result result) "line 10"))
        (should (str/includes? (:result result) "line 14"))
        (should-not (str/includes? (:result result) "line 15"))))

    (it "allows reading within the crew quarters"
      (let [state-dir   test-dir
            quarters    (str state-dir "/crew/main")
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file quarters))
        (spit (str quarters "/notes.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str quarters "/notes.txt")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should= "1: hello" (:result result)))))

    (it "allows reading within explicit whitelisted directories"
      (let [state-dir   test-dir
            session-key "main-session"
            whitelisted (str test-dir "/playground")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file whitelisted))
        (spit (str whitelisted "/data.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                              :crew {"main" {:tools {:allow ["read"]
                                                                                       :directories [whitelisted]}}}
                                                              :models {}
                                                              :providers {}})]
                       (sut/read-tool {"file_path"   (str whitelisted "/data.txt")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should= "1: hello" (:result result)))))

    (it "rejects reading outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   "/etc/passwd"
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "allows reading in session cwd only with :cwd opt in"
      (let [state-dir   test-dir
            session-key "main-session"
            cwd         (str test-dir "/project")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                              :crew {"main" {:tools {:allow ["read"]
                                                                                       :directories [:cwd]}}}
                                                              :models {}
                                                              :providers {}})]
                       (sut/read-tool {"file_path"   (str cwd "/hello.txt")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should= "1: hi there" (:result result)))))

    (it "rejects reading the session cwd without :cwd opt in"
      (let [state-dir   test-dir
            session-key "main-session"
            cwd         (str test-dir "/project")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str cwd "/hello.txt")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects path traversal that escapes the quarters"
      (let [state-dir   test-dir
            session-key "main-session"
            quarters    (str state-dir "/crew/main")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str quarters "/../../etc/passwd")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects reading the config directory"
      (let [state-dir   test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str state-dir "/config/crew/main.edn")
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result)))))))

  ;; endregion ^^^^^ read ^^^^^

  ;; region ----- write -----

  (describe "write"

    (it "creates a new file with the given content"
      (let [path   (str test-dir "/new.txt")
            result (sut/write-tool {"file_path" path "content" "hello world"})]
        (should-be-nil (:isError result))
        (should= "hello world" (slurp path))))

    (it "overwrites an existing file"
      (write-file! "existing.txt" "old content")
      (sut/write-tool {"file_path" (str test-dir "/existing.txt") "content" "new content"})
      (should= "new content" (read-file "existing.txt")))

    (it "creates parent directories if needed"
      (let [path   (str test-dir "/sub/dir/file.txt")
            result (sut/write-tool {"file_path" path "content" "deep"})]
        (should-be-nil (:isError result))
        (should= "deep" (slurp path))))

    (it "returns a success message"
      (let [result (sut/write-tool {"file_path" (str test-dir "/ok.txt") "content" "ok"})]
        (should (string? (:result result)))))

    (it "auto-creates the crew quarters on first use"
      (let [state-dir   test-dir
            session-key "main-session"
            path        (str state-dir "/crew/main/new.txt")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["write"]}}} :models {} :providers {}})]
                       (sut/write-tool {"file_path"   path
                                        "content"     "hello"
                                        "session_key" session-key
                                        "state_dir"   state-dir}))]
          (should= "hello" (slurp path))
          (should (string? (:result result))))))

    (it "rejects writes outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"
            result      (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["write"]}}} :models {} :providers {}})]
                          (do
                            (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
                            (sut/write-tool {"file_path"   "/tmp/evil.txt"
                                             "content"     "evil"
                                             "session_key" session-key
                                             "state_dir"   state-dir})))]
        (should (:isError result))
        (should (re-find #"path outside allowed directories" (:error result))))))

  ;; endregion ^^^^^ write ^^^^^

  ;; region ----- edit -----

  (describe "edit"

    (it "replaces matching text"
      (write-file! "code.txt" "foo = 1\nbar = 2")
      (let [result (sut/edit-tool {"file_path"  (str test-dir "/code.txt")
                                   "old_string" "foo = 1"
                                   "new_string" "foo = 42"})]
        (should-be-nil (:isError result))
        (should= "foo = 42\nbar = 2" (read-file "code.txt"))))

    (it "returns error when string not found"
      (write-file! "code.txt" "foo = 1")
      (let [result (sut/edit-tool {"file_path"  (str test-dir "/code.txt")
                                   "old_string" "not here"
                                   "new_string" "replacement"})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "returns error when multiple matches and replace_all not set"
      (write-file! "code.txt" "x = 1\nx = 1\nx = 1")
      (let [result (sut/edit-tool {"file_path"  (str test-dir "/code.txt")
                                   "old_string" "x = 1"
                                   "new_string" "x = 2"})]
        (should (:isError result))
        (should (re-find #"multiple" (:error result)))))

    (it "replaces all occurrences when replace_all is true"
      (write-file! "code.txt" "x = 1\ny = 2\nx = 1")
      (let [result (sut/edit-tool {"file_path"   (str test-dir "/code.txt")
                                   "old_string"  "x = 1"
                                   "new_string"  "x = 99"
                                   "replace_all" true})]
        (should-be-nil (:isError result))
        (should= "x = 99\ny = 2\nx = 99" (read-file "code.txt"))))

    (it "returns error for missing file"
      (let [result (sut/edit-tool {"file_path"  (str test-dir "/missing.txt")
                                   "old_string" "x"
                                   "new_string" "y"})]
        (should (:isError result)))))

  ;; endregion ^^^^^ edit ^^^^^

  ;; region ----- grep -----

  (describe "grep"

    (it "returns matching lines with file and line prefixes"
      (write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
      (let [result (sut/grep-tool {"pattern" "defn" "path" (str test-dir "/src")})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj:1:"))
        (should (str/includes? (:result result) "(defn greet [name])"))
        (should (str/includes? (:result result) "core.clj:2:"))))

    (it "returns a clear no-matches result"
      (write-file! "src/core.clj" "(defn greet [name])")
      (let [result (sut/grep-tool {"pattern" "xyzzy" "path" (str test-dir "/src")})]
        (should-be-nil (:isError result))
        (should= "no matches" (:result result))))

    (it "limits matches using a glob filter"
      (write-file! "src/core.clj" "(defn greet [name])")
      (write-file! "src/notes.md" "defn is a Clojure macro")
      (let [result (sut/grep-tool {"pattern" "defn" "path" (str test-dir "/src") "glob" "*.clj"})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj"))
        (should-not (str/includes? (:result result) "notes.md"))))

    (it "truncates output at the requested head limit"
      (write-file! "big.txt" (str/join "\n" (map #(str "line " %) (range 1 11))))
      (let [result (sut/grep-tool {"pattern" "line" "path" (str test-dir "/big.txt") "head_limit" 5})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "line 1"))
        (should (str/includes? (:result result) "line 5"))
        (should-not (str/includes? (:result result) "line 6"))
        (should (str/includes? (:result result) "truncated"))))

    (it "rejects grep outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["grep"]}}} :models {} :providers {}})]
                       (sut/grep-tool {"pattern"     "hunter"
                                       "path"        "/tmp/secret-stash"
                                       "session_key" session-key
                                       "state_dir"   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "returns count mode output per file"
      (write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
      (write-file! "src/util.clj" "(defn only [name])")
      (let [result (sut/grep-tool {"pattern" "defn" "path" (str test-dir "/src") "output_mode" "count"})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj:2"))
        (should (str/includes? (:result result) "util.clj:1")))))

  ;; endregion ^^^^^ grep ^^^^^

  ;; region ----- glob -----

  (describe "glob"

    (it "returns matching file paths"
      (write-file! "src/core.clj" "")
      (write-file! "src/util.clj" "")
      (write-file! "src/notes.md" "")
      (set-mtime! "src/core.clj" "2026-04-20T00:00:01Z")
      (set-mtime! "src/util.clj" "2026-04-20T00:00:02Z")
      (set-mtime! "src/notes.md" "2026-04-20T00:00:03Z")
      (let [result (sut/glob-tool {"pattern" "**/*.clj" "state_dir" test-dir})]
        (should-be-nil (:isError result))
        (should= "src/util.clj\nsrc/core.clj" (:result result))))

    (it "sorts results by modification time descending with alphabetical tiebreaker"
      (write-file! "src/b.clj" "")
      (write-file! "src/a.clj" "")
      (set-mtime! "src/a.clj" "2026-04-20T00:00:00Z")
      (set-mtime! "src/b.clj" "2026-04-20T00:00:00Z")
      (let [result (sut/glob-tool {"pattern" "src/*.clj" "state_dir" test-dir})]
        (should-be-nil (:isError result))
        (should= "src/a.clj\nsrc/b.clj" (:result result))))

    (it "returns no matches when nothing matches the pattern"
      (write-file! "README.md" "")
      (let [result (sut/glob-tool {"pattern" "**/*.clj" "state_dir" test-dir})]
        (should-be-nil (:isError result))
        (should= "no matches" (:result result))))

    (it "uses the default head limit with truncation metadata"
      (write-file! "a.clj" "")
      (write-file! "b.clj" "")
      (write-file! "c.clj" "")
      (write-file! "d.clj" "")
      (write-file! "e.clj" "")
      (doseq [[name instant] [["a.clj" "2026-04-20T00:00:01Z"]
                              ["b.clj" "2026-04-20T00:00:02Z"]
                              ["c.clj" "2026-04-20T00:00:03Z"]
                              ["d.clj" "2026-04-20T00:00:04Z"]
                              ["e.clj" "2026-04-20T00:00:05Z"]]]
        (set-mtime! name instant))
      (binding [glob/*default-head-limit* 3]
        (let [result (sut/glob-tool {"pattern" "*.clj" "state_dir" test-dir})]
          (should-be-nil (:isError result))
          (should= "e.clj\nd.clj\nc.clj\nResults truncated. 5 total matches." (:result result)))))

    (it "defaults the search path to the session cwd"
      (let [state-dir   test-dir
            cwd         (str test-dir "/workspace")
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (write-file! "workspace/src/core.clj" "")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                               :crew {"main" {:tools {:allow ["glob"]
                                                                                        :directories [:cwd]}}}
                                                               :models {}
                                                               :providers {}})]
                       (sut/glob-tool {"pattern" "**/*.clj"
                                        "session_key" session-key
                                        "state_dir" state-dir}))]
          (should-be-nil (:isError result))
          (should= "src/core.clj" (:result result)))))

    (it "rejects glob outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["glob"]}}} :models {} :providers {}})]
                       (sut/glob-tool {"pattern" "*.clj"
                                        "path" "/tmp/secret-stash"
                                        "session_key" session-key
                                        "state_dir" state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result)))))))

  ;; endregion ^^^^^ glob ^^^^^

  ;; region ----- web_fetch -----

  (describe "web_fetch"

    (it "returns the body text of a URL"
      (let [result (with-redefs [http/get (fn [_ _] {:status 200 :headers {"content-type" "text/plain"} :body "Hello, world."})]
                     (sut/web-fetch-tool {"url" "http://example.local/hello"}))]
        (should-be-nil (:isError result))
        (should= 200 (:status result))
        (should= "Hello, world." (:result result))))

    (it "strips script and style blocks from html by default"
      (let [body   "<html><head><style>body { color: red; }</style><script>var secrets = 1;</script></head><body><h1>Hello</h1><p>Main content.</p></body></html>"
            result (with-redefs [http/get (fn [_ _] {:status 200 :headers {"content-type" "text/html"} :body body})]
                     (sut/web-fetch-tool {"url" "http://example.local/page"}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "Hello"))
        (should (str/includes? (:result result) "Main content."))
        (should-not (str/includes? (:result result) "color: red"))
        (should-not (str/includes? (:result result) "var secrets"))))

    (it "preserves the raw body when format is raw"
      (let [body   "<script>var secret = 1;</script><h1>Title</h1>"
            result (with-redefs [http/get (fn [_ _] {:status 200 :headers {"content-type" "text/html"} :body body})]
                     (sut/web-fetch-tool {"url" "http://example.local/page" "format" "raw"}))]
        (should-be-nil (:isError result))
        (should= body (:result result))))

    (it "truncates output at the default line limit"
      (let [body   (str/join "\n" (map #(str "line " %) (range 1 6)))
            result (binding [web-fetch/*default-limit* 3]
                     (with-redefs [http/get (fn [_ _] {:status 200 :headers {"content-type" "text/plain"} :body body})]
                       (sut/web-fetch-tool {"url" "http://example.local/long"})))]
        (should-be-nil (:isError result))
        (should= "line 1\nline 2\nline 3\nResults truncated. 5 total lines." (:result result))))

    (it "refuses binary content types"
      (let [result (with-redefs [http/get (fn [_ _] {:status 200 :headers {"content-type" "image/png"} :body ""})]
                     (sut/web-fetch-tool {"url" "http://example.local/image.png"}))]
        (should (:isError result))
        (should (re-find #"binary content-type" (:error result)))))

    (it "follows redirects up to the final body"
      (let [result (with-redefs [http/get (fn [url _]
                                           (case url
                                             "http://example.local/old" {:status 301 :headers {"location" "http://example.local/new"} :body ""}
                                             "http://example.local/new" {:status 200 :headers {"content-type" "text/plain"} :body "Moved here."}))]
                     (sut/web-fetch-tool {"url" "http://example.local/old"}))]
        (should-be-nil (:isError result))
        (should= 200 (:status result))
        (should= "Moved here." (:result result)))))

  ;; endregion ^^^^^ web_fetch ^^^^^

  ;; region ----- web_search -----

  (describe "web_search"

    (it "returns ranked result entries"
      (let [body   (json/generate-string {:web {:results [{:title "core.async guide"
                                                           :url "https://clojure.org/async"
                                                           :description "Channels and go blocks"}
                                                          {:title "Rich Hickey talk"
                                                           :url "https://youtu.be/hMIZ9g6ucs"
                                                           :description "Intro to core.async"}]}})
            result (with-redefs [config/load-config (fn [& _] {:tools {:web_search {:provider :brave :api-key "brave-key"}}})
                                 http/get (fn [_ _] {:status 200 :headers {"content-type" "application/json"} :body body})]
                     (sut/web-search-tool {"query" "clojure core async" "state_dir" test-dir}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "1. core.async guide"))
        (should (str/includes? (:result result) "https://clojure.org/async"))
        (should (str/includes? (:result result) "2. Rich Hickey talk"))))

    (it "limits output to num_results"
      (let [body   (json/generate-string {:web {:results [{:title "Guide 1" :url "https://example.com/1" :description "snippet 1"}
                                                          {:title "Guide 2" :url "https://example.com/2" :description "snippet 2"}
                                                          {:title "Guide 3" :url "https://example.com/3" :description "snippet 3"}]}})
            result (with-redefs [config/load-config (fn [& _] {:tools {:web_search {:provider :brave :api-key "brave-key"}}})
                                 http/get (fn [_ _] {:status 200 :headers {"content-type" "application/json"} :body body})]
                     (sut/web-search-tool {"query" "clojure" "num_results" 2 "state_dir" test-dir}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "1. Guide 1"))
        (should (str/includes? (:result result) "2. Guide 2"))
        (should-not (str/includes? (:result result) "Guide 3"))))

    (it "returns no results when the provider returns none"
      (let [body   (json/generate-string {:web {:results []}})
            result (with-redefs [config/load-config (fn [& _] {:tools {:web_search {:provider :brave :api-key "brave-key"}}})
                                 http/get (fn [_ _] {:status 200 :headers {"content-type" "application/json"} :body body})]
                     (sut/web-search-tool {"query" "ajshdkajshdakjsh" "state_dir" test-dir}))]
        (should-be-nil (:isError result))
        (should= "no results" (:result result))))

    (it "returns a config error when no api key is configured"
      (let [result (with-redefs [config/load-config (fn [& _] {:tools {:web_search {:provider :brave}}})]
                     (sut/web-search-tool {"query" "clojure" "state_dir" test-dir}))]
        (should (:isError result))
        (should (str/includes? (:error result) "web_search"))
        (should (str/includes? (:error result) "api_key"))))

    (it "registers web_search when it is allowed"
      (sut/register-all! #{"web_search"})
      (should= #{"web_search"} (set (map :name (registry/all-tools))))))

  ;; endregion ^^^^^ web_search ^^^^^

  ;; region ----- exec -----

  (describe "exec"

    (it "runs a shell command and returns output"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "hello world\n")
                                 sut/process-exit-value (fn [_] 0)]
                     (sut/exec-tool {"command" "echo hello world"}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "hello world"))))

    (it "returns error on non-zero exit"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "boom\n")
                                 sut/process-exit-value (fn [_] 1)]
                     (sut/exec-tool {"command" "exit 1"}))]
        (should (:isError result))))

    (it "respects workdir option"
      (let [captured-workdir (atom nil)
            result           (with-redefs [sut/start-process (fn [args]
                                                               (reset! captured-workdir (get args "workdir"))
                                                               ::proc)
                                           sut/process-finished? (fn [_ _] true)
                                           sut/read-process-output (fn [_] "target.txt\n")
                                           sut/process-exit-value (fn [_] 0)]
                                (sut/exec-tool {"command" "ls" "workdir" (str test-dir "/subdir")}))]
        (should= (str test-dir "/subdir") @captured-workdir)
        (should (str/includes? (:result result) "target.txt"))))

    (it "uses the session cwd as implicit workdir when none is provided"
      (let [captured-workdir (atom nil)
            session-key      "exec-session"
            cwd              (str test-dir "/exec-cwd")]
        (helper/create-session! test-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (let [result (with-redefs [sut/start-process      (fn [args]
                                                             (reset! captured-workdir (get args "workdir"))
                                                             ::proc)
                                   sut/process-finished? (fn [_ _] true)
                                   sut/read-process-output (fn [_] "ok\n")
                                   sut/process-exit-value (fn [_] 0)]
                       (sut/exec-tool {"command" "pwd" "session_key" session-key "state_dir" test-dir}))]
          (should= cwd @captured-workdir)
          (should= "ok" (:result result)))))

    (it "prefers explicit workdir over the session cwd"
      (let [captured-workdir (atom nil)
            session-key      "exec-session-explicit"
            cwd              (str test-dir "/exec-cwd")
            explicit         (str test-dir "/explicit")]
        (helper/create-session! test-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (.mkdirs (io/file explicit))
        (let [result (with-redefs [sut/start-process      (fn [args]
                                                             (reset! captured-workdir (get args "workdir"))
                                                             ::proc)
                                   sut/process-finished? (fn [_ _] true)
                                   sut/read-process-output (fn [_] "ok\n")
                                   sut/process-exit-value (fn [_] 0)]
                       (sut/exec-tool {"command" "pwd"
                                       "workdir" explicit
                                       "session_key" session-key
                                       "state_dir" test-dir}))]
          (should= explicit @captured-workdir)
          (should= "ok" (:result result)))))

    (it "ignores the session cwd when it is not a directory"
      (let [captured-workdir (atom ::unset)
            session-key      "exec-session-missing-cwd"
            cwd              (str test-dir "/missing-dir")]
        (helper/create-session! test-dir session-key {:crew "main" :cwd cwd})
        (let [result (with-redefs [sut/start-process      (fn [args]
                                                             (reset! captured-workdir (get args "workdir" ::missing))
                                                             ::proc)
                                   sut/process-finished? (fn [_ _] true)
                                   sut/read-process-output (fn [_] "ok\n")
                                   sut/process-exit-value (fn [_] 0)]
                       (sut/exec-tool {"command" "pwd" "session_key" session-key "state_dir" test-dir}))]
          (should= ::missing @captured-workdir)
          (should= "ok" (:result result)))))

    (it "falls back to the default timeout when timeout is not an integer"
      (let [polls (atom [])]
        (with-redefs [sut/start-process      (fn [_] ::proc)
                      sut/process-finished? (fn [_ wait-ms]
                                              (swap! polls conj wait-ms)
                                              true)
                      sut/read-process-output (fn [_] "ok\n")
                      sut/process-exit-value (fn [_] 0)]
          (should= "ok" (:result (sut/exec-tool {"command" "pwd" "timeout" "bogus"}))))
        (should= [50] @polls)))

    (it "returns an error when process startup throws"
      (let [result (with-redefs [sut/start-process (fn [_] (throw (ex-info "boom" {})))]
                     (sut/exec-tool {"command" "ignored"}))]
        (should (:isError result))
        (should= "boom" (:error result))))

    (it "captures stderr in the output"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "err\n")
                                 sut/process-exit-value (fn [_] 0)]
                     (sut/exec-tool {"command" "echo err >&2"}))]
        (should (string? (:result result)))))

    (it "returns error on timeout"
      (let [destroyed (atom false)
            result    (with-redefs [sut/start-process (fn [_] ::proc)
                                    sut/process-finished? (fn [_ _] @destroyed)
                                    sut/destroy-process! (fn [_ _] (reset! destroyed true))]
                        (sut/exec-tool {"command" "ignored" "timeout" 1}))]
        (should (:isError result))
        (should (re-find #"(?i)timeout" (:error result)))))

    (it "polls only the remaining timeout window before timing out"
      (let [polls  (atom [])
            result (with-redefs [sut/start-process      (fn [_] ::proc)
                                 sut/process-finished? (fn [_ wait-ms]
                                                         (swap! polls conj wait-ms)
                                                         false)
                                 sut/destroy-process!  (fn [& _] nil)]
                     (sut/exec-tool {"command" "ignored" "timeout" 75}))]
        (should (:isError result))
        (should= [50 25] @polls)))

    (it "uses shortened cleanup grace on timeout"
      (let [destroyed (atom false)
            grace-ms  (atom nil)
            result    (with-redefs [sut/start-process      (fn [_] ::proc)
                                    sut/process-finished? (fn [_ _] @destroyed)
                                    sut/destroy-process!  (fn [_ ms]
                                                            (reset! grace-ms ms)
                                                            (reset! destroyed true))]
                        (sut/exec-tool {"command" "ignored" "timeout" 1}))]
        (should (:isError result))
        (should= 10 @grace-ms)))

    (it "returns cancelled when the session is cancelled mid-command"
      (let [turn      (bridge/begin-turn! "exec-cancel")
            started?  (promise)
            result    (future
                        (with-redefs [sut/start-process (fn [_]
                                                         (deliver started? true)
                                                         ::proc)
                                      sut/process-finished? (fn [_ _] false)
                                      sut/destroy-process! (fn [& _] nil)]
                          (sut/exec-tool {"command" "ignored" "session_key" "exec-cancel"}))) ]
        @started?
        (bridge/cancel! "exec-cancel")
        (should= :cancelled (:error (deref result 1000 nil)))
        (bridge/end-turn! "exec-cancel" turn))))

  ;; endregion ^^^^^ exec ^^^^^

  ;; region ----- session_info / session_model -----

  (let [base-cfg {:defaults  {:crew "main" :model "grover"}
                  :crew      {"main" {:model :grover :soul "You are Isaac."}}
                  :models    {"grover" {:model "echo" :provider :grover :context-window 32768}
                              "parrot" {:model "squawk" :provider :grover :context-window 16384}}
                  :providers {}}]

    (describe "session_info"

      (it "returns current session state with snake_case keys"
        (helper/create-session! test-dir "si-basic" {:crew "main" :cwd test-dir})
        (helper/update-session! test-dir "si-basic" {:createdAt "2026-04-27T10:00:00" :updated-at "2026-04-27T10:00:00"})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-info-tool {"session_key" "si-basic" "state_dir" test-dir}))
              data   (json/parse-string (:result result) true)]
          (should= "main" (:crew data))
          (should= "grover" (get-in data [:model :alias]))
          (should= "echo" (get-in data [:model :upstream]))
          (should= "grover" (:provider data))
          (should= "si-basic" (:session data))
          (should= 0 (:compactions data))
          (should= 0 (get-in data [:context :used]))
          (should= 32768 (get-in data [:context :window]))
          (should= "2026-04-27T10:00:00Z" (:created_at data)))))

    (describe "session_model"

      (it "switches model when model arg is provided"
        (helper/create-session! test-dir "sm-switch" {:crew "main" :cwd test-dir})
        (helper/update-session! test-dir "sm-switch" {:compaction-disabled true
                                                         :compaction {:consecutive-failures 5}})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-model-tool {"session_key" "sm-switch" "model" "parrot" "state_dir" test-dir}))
              data   (json/parse-string (:result result) true)]
          (should= "parrot" (get-in data [:model :alias]))
          (should= "squawk" (get-in data [:model :upstream]))
          (should= "parrot" (:model (helper/get-session test-dir "sm-switch")))
          (should= false (:compaction-disabled (helper/get-session test-dir "sm-switch")))
          (should= 0 (get-in (helper/get-session test-dir "sm-switch") [:compaction :consecutive-failures]))))

      (it "resets model to crew default when reset is true"
        (helper/create-session! test-dir "sm-reset" {:crew "main" :cwd test-dir})
        (helper/update-session! test-dir "sm-reset" {:model "parrot"})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                      (sut/session-model-tool {"session_key" "sm-reset" "reset" true "state_dir" test-dir}))
              data   (json/parse-string (:result result) true)]
          (should= "grover" (get-in data [:model :alias]))
          (should= "grover" (:model (helper/get-session test-dir "sm-reset")))))

      (it "errors when both model and reset are provided"
        (helper/create-session! test-dir "sm-both" {:crew "main" :cwd test-dir})
        (let [result (sut/session-model-tool {"session_key" "sm-both" "model" "grover" "reset" true "state_dir" test-dir})]
          (should (:isError result))
          (should (str/includes? (:error result) "mutually exclusive"))))

      (it "errors when model alias does not exist"
        (helper/create-session! test-dir "sm-nomodel" {:crew "main" :cwd test-dir})
        (let [result (with-redefs [config/load-config (fn [& _] base-cfg)]
                       (sut/session-model-tool {"session_key" "sm-nomodel" "model" "nonexistent" "state_dir" test-dir}))]
          (should (:isError result))
          (should (str/includes? (:error result) "unknown model: nonexistent"))))))

  ;; endregion ^^^^^ session_info / session_model ^^^^^

  ;; region ----- registration -----

  (describe "register-all!"

    (it "registers only the explicitly allowed tools when an allow list is provided"
      (sut/register-all! #{"read" "write"})
      (should= #{"read" "write"} (set (map :name (registry/all-tools)))))

    (it "skips grep registration and logs a warning when rg is not on path"
      (with-redefs [shell/cmd-available? (constantly false)]
        (log/capture-logs
          (sut/register-all! #{"grep"})
          (should= [] (registry/all-tools))
          (should= 1 (count @log/captured-logs))
          (let [entry (first @log/captured-logs)]
            (should= :warn (:level entry))
            (should= :tool/register-skipped (:event entry))
            (should= "grep" (:tool entry))
            (should= "available? returned false" (:reason entry))))))

    (it "registers glob when it is allowed"
      (sut/register-all! #{"glob"})
      (should= #{"glob"} (set (map :name (registry/all-tools)))))

    (it "registers web_fetch when it is allowed"
      (sut/register-all! #{"web_fetch"})
      (should= #{"web_fetch"} (set (map :name (registry/all-tools))))))

  ;; endregion ^^^^^ registration ^^^^^

  )
