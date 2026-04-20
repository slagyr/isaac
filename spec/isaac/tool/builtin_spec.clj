(ns isaac.tool.builtin-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.session.storage :as storage]
    [isaac.session.bridge :as bridge]
    [isaac.tool.glob :as glob]
    [isaac.tool.builtin :as sut]
    [isaac.tool.registry :as registry]
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
      (let [result (sut/read-tool {:filePath (str test-dir "/hello.txt")})]
        (should= "1: Hello, world!" (:result result))
        (should-be-nil (:isError result))))

    (it "returns multi-line file contents"
      (write-file! "multi.txt" "line one\nline two\nline three")
      (let [result (sut/read-tool {:filePath (str test-dir "/multi.txt")})]
        (should (str/includes? (:result result) "line one"))
        (should (str/includes? (:result result) "line three"))))

    (it "returns error for missing file"
      (let [result (sut/read-tool {:filePath (str test-dir "/no-such-file.txt")})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "lists directory contents"
      (.mkdirs (io/file (str test-dir "/mydir")))
      (write-file! "mydir/a.txt" "a")
      (write-file! "mydir/b.txt" "b")
      (let [result (sut/read-tool {:filePath (str test-dir "/mydir")})]
        (should (str/includes? (:result result) "a.txt"))
        (should (str/includes? (:result result) "b.txt"))))

    (it "respects offset to skip leading lines"
      (write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {:filePath (str test-dir "/numbered.txt") :offset 10})]
        (should-not (str/includes? (:result result) "line 9"))
        (should (str/includes? (:result result) "line 10"))))

    (it "respects limit to cap returned lines"
      (write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {:filePath (str test-dir "/numbered.txt") :offset 10 :limit 5})]
        (should (str/includes? (:result result) "line 10"))
        (should (str/includes? (:result result) "line 14"))
        (should-not (str/includes? (:result result) "line 15"))))

    (it "allows reading within the crew quarters"
      (let [state-dir   test-dir
            quarters    (str state-dir "/crew/main")
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file quarters))
        (spit (str quarters "/notes.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {:filePath   (str quarters "/notes.txt")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should= "1: hello" (:result result)))))

    (it "allows reading within explicit whitelisted directories"
      (let [state-dir   test-dir
            session-key "main-session"
            whitelisted (str test-dir "/playground")]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file whitelisted))
        (spit (str whitelisted "/data.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                              :crew {"main" {:tools {:allow ["read"]
                                                                                       :directories [whitelisted]}}}
                                                              :models {}
                                                              :providers {}})]
                       (sut/read-tool {:filePath   (str whitelisted "/data.txt")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should= "1: hello" (:result result)))))

    (it "rejects reading outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {:filePath   "/etc/passwd"
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "allows reading in session cwd only with :cwd opt in"
      (let [state-dir   test-dir
            session-key "main-session"
            cwd         (str test-dir "/project")]
        (storage/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                              :crew {"main" {:tools {:allow ["read"]
                                                                                       :directories [:cwd]}}}
                                                              :models {}
                                                              :providers {}})]
                       (sut/read-tool {:filePath   (str cwd "/hello.txt")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should= "1: hi there" (:result result)))))

    (it "rejects reading the session cwd without :cwd opt in"
      (let [state-dir   test-dir
            session-key "main-session"
            cwd         (str test-dir "/project")]
        (storage/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {:filePath   (str cwd "/hello.txt")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects path traversal that escapes the quarters"
      (let [state-dir   test-dir
            session-key "main-session"
            quarters    (str state-dir "/crew/main")]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {:filePath   (str quarters "/../../etc/passwd")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects reading the config directory"
      (let [state-dir   test-dir
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {:filePath   (str state-dir "/config/crew/main.edn")
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result)))))))

  ;; endregion ^^^^^ read ^^^^^

  ;; region ----- write -----

  (describe "write"

    (it "creates a new file with the given content"
      (let [path   (str test-dir "/new.txt")
            result (sut/write-tool {:filePath path :content "hello world"})]
        (should-be-nil (:isError result))
        (should= "hello world" (slurp path))))

    (it "overwrites an existing file"
      (write-file! "existing.txt" "old content")
      (sut/write-tool {:filePath (str test-dir "/existing.txt") :content "new content"})
      (should= "new content" (read-file "existing.txt")))

    (it "creates parent directories if needed"
      (let [path   (str test-dir "/sub/dir/file.txt")
            result (sut/write-tool {:filePath path :content "deep"})]
        (should-be-nil (:isError result))
        (should= "deep" (slurp path))))

    (it "returns a success message"
      (let [result (sut/write-tool {:filePath (str test-dir "/ok.txt") :content "ok"})]
        (should (string? (:result result)))))

    (it "auto-creates the crew quarters on first use"
      (let [state-dir   test-dir
            session-key "main-session"
            path        (str state-dir "/crew/main/new.txt")]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["write"]}}} :models {} :providers {}})]
                       (sut/write-tool {:filePath   path
                                        :content    "hello"
                                        :session-key session-key
                                        :state-dir   state-dir}))]
          (should= "hello" (slurp path))
          (should (string? (:result result))))))

    (it "rejects writes outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"
            result      (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["write"]}}} :models {} :providers {}})]
                          (do
                            (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
                            (sut/write-tool {:filePath   "/tmp/evil.txt"
                                             :content    "evil"
                                             :session-key session-key
                                             :state-dir   state-dir})))]
        (should (:isError result))
        (should (re-find #"path outside allowed directories" (:error result))))))

  ;; endregion ^^^^^ write ^^^^^

  ;; region ----- edit -----

  (describe "edit"

    (it "replaces matching text"
      (write-file! "code.txt" "foo = 1\nbar = 2")
      (let [result (sut/edit-tool {:filePath  (str test-dir "/code.txt")
                                   :oldString "foo = 1"
                                   :newString "foo = 42"})]
        (should-be-nil (:isError result))
        (should= "foo = 42\nbar = 2" (read-file "code.txt"))))

    (it "returns error when string not found"
      (write-file! "code.txt" "foo = 1")
      (let [result (sut/edit-tool {:filePath  (str test-dir "/code.txt")
                                   :oldString "not here"
                                   :newString "replacement"})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "returns error when multiple matches and replaceAll not set"
      (write-file! "code.txt" "x = 1\nx = 1\nx = 1")
      (let [result (sut/edit-tool {:filePath  (str test-dir "/code.txt")
                                   :oldString "x = 1"
                                   :newString "x = 2"})]
        (should (:isError result))
        (should (re-find #"multiple" (:error result)))))

    (it "replaces all occurrences when replaceAll is true"
      (write-file! "code.txt" "x = 1\ny = 2\nx = 1")
      (let [result (sut/edit-tool {:filePath   (str test-dir "/code.txt")
                                   :oldString  "x = 1"
                                   :newString  "x = 99"
                                   :replaceAll true})]
        (should-be-nil (:isError result))
        (should= "x = 99\ny = 2\nx = 99" (read-file "code.txt"))))

    (it "returns error for missing file"
      (let [result (sut/edit-tool {:filePath  (str test-dir "/missing.txt")
                                   :oldString "x"
                                   :newString "y"})]
        (should (:isError result)))))

  ;; endregion ^^^^^ edit ^^^^^

  ;; region ----- grep -----

  (describe "grep"

    (it "returns matching lines with file and line prefixes"
      (write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
      (let [result (sut/grep-tool {:pattern "defn" :path (str test-dir "/src")})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj:1:"))
        (should (str/includes? (:result result) "(defn greet [name])"))
        (should (str/includes? (:result result) "core.clj:2:"))))

    (it "returns a clear no-matches result"
      (write-file! "src/core.clj" "(defn greet [name])")
      (let [result (sut/grep-tool {:pattern "xyzzy" :path (str test-dir "/src")})]
        (should-be-nil (:isError result))
        (should= "no matches" (:result result))))

    (it "limits matches using a glob filter"
      (write-file! "src/core.clj" "(defn greet [name])")
      (write-file! "src/notes.md" "defn is a Clojure macro")
      (let [result (sut/grep-tool {:pattern "defn" :path (str test-dir "/src") :glob "*.clj"})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj"))
        (should-not (str/includes? (:result result) "notes.md"))))

    (it "truncates output at the requested head limit"
      (write-file! "big.txt" (str/join "\n" (map #(str "line " %) (range 1 11))))
      (let [result (sut/grep-tool {:pattern "line" :path (str test-dir "/big.txt") :head_limit 5})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "line 1"))
        (should (str/includes? (:result result) "line 5"))
        (should-not (str/includes? (:result result) "line 6"))
        (should (str/includes? (:result result) "truncated"))))

    (it "rejects grep outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["grep"]}}} :models {} :providers {}})]
                       (sut/grep-tool {:pattern     "hunter"
                                       :path        "/tmp/secret-stash"
                                       :session-key session-key
                                       :state-dir   state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "returns count mode output per file"
      (write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
      (write-file! "src/util.clj" "(defn only [name])")
      (let [result (sut/grep-tool {:pattern "defn" :path (str test-dir "/src") :output_mode "count"})]
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
      (let [result (sut/glob-tool {:pattern "**/*.clj" :state-dir test-dir})]
        (should-be-nil (:isError result))
        (should= "src/util.clj\nsrc/core.clj" (:result result))))

    (it "sorts results by modification time descending with alphabetical tiebreaker"
      (write-file! "src/b.clj" "")
      (write-file! "src/a.clj" "")
      (set-mtime! "src/a.clj" "2026-04-20T00:00:00Z")
      (set-mtime! "src/b.clj" "2026-04-20T00:00:00Z")
      (let [result (sut/glob-tool {:pattern "src/*.clj" :state-dir test-dir})]
        (should-be-nil (:isError result))
        (should= "src/a.clj\nsrc/b.clj" (:result result))))

    (it "returns no matches when nothing matches the pattern"
      (write-file! "README.md" "")
      (let [result (sut/glob-tool {:pattern "**/*.clj" :state-dir test-dir})]
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
        (let [result (sut/glob-tool {:pattern "*.clj" :state-dir test-dir})]
          (should-be-nil (:isError result))
          (should= "e.clj\nd.clj\nc.clj\nResults truncated. 5 total matches." (:result result)))))

    (it "defaults the search path to the session cwd"
      (let [state-dir   test-dir
            cwd         (str test-dir "/workspace")
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (write-file! "workspace/src/core.clj" "")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                               :crew {"main" {:tools {:allow ["glob"]
                                                                                        :directories [:cwd]}}}
                                                               :models {}
                                                               :providers {}})]
                       (sut/glob-tool {:pattern "**/*.clj"
                                       :session-key session-key
                                       :state-dir state-dir}))]
          (should-be-nil (:isError result))
          (should= "src/core.clj" (:result result)))))

    (it "rejects glob outside allowed directories"
      (let [state-dir   test-dir
            session-key "main-session"]
        (storage/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["glob"]}}} :models {} :providers {}})]
                       (sut/glob-tool {:pattern "*.clj"
                                       :path "/tmp/secret-stash"
                                       :session-key session-key
                                       :state-dir state-dir}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result)))))))

  ;; endregion ^^^^^ glob ^^^^^

  ;; region ----- exec -----

  (describe "exec"

    (it "runs a shell command and returns output"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "hello world\n")
                                 sut/process-exit-value (fn [_] 0)]
                     (sut/exec-tool {:command "echo hello world"}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "hello world"))))

    (it "returns error on non-zero exit"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "boom\n")
                                 sut/process-exit-value (fn [_] 1)]
                     (sut/exec-tool {:command "exit 1"}))]
        (should (:isError result))))

    (it "respects workdir option"
      (let [captured-workdir (atom nil)
            result           (with-redefs [sut/start-process (fn [{:keys [workdir]}]
                                                              (reset! captured-workdir workdir)
                                                              ::proc)
                                           sut/process-finished? (fn [_ _] true)
                                           sut/read-process-output (fn [_] "target.txt\n")
                                           sut/process-exit-value (fn [_] 0)]
                               (sut/exec-tool {:command "ls" :workdir (str test-dir "/subdir")}))]
        (should= (str test-dir "/subdir") @captured-workdir)
        (should (str/includes? (:result result) "target.txt"))))

    (it "captures stderr in the output"
      (let [result (with-redefs [sut/start-process (fn [_] ::proc)
                                 sut/process-finished? (fn [_ _] true)
                                 sut/read-process-output (fn [_] "err\n")
                                 sut/process-exit-value (fn [_] 0)]
                     (sut/exec-tool {:command "echo err >&2"}))]
        (should (string? (:result result)))))

    (it "returns error on timeout"
      (let [destroyed (atom false)
            result    (with-redefs [sut/start-process (fn [_] ::proc)
                                    sut/process-finished? (fn [_ _] @destroyed)
                                    sut/destroy-process! (fn [_] (reset! destroyed true))]
                        (sut/exec-tool {:command "ignored" :timeout 1}))]
        (should (:isError result))
        (should (re-find #"(?i)timeout" (:error result)))))

    (it "returns cancelled when the session is cancelled mid-command"
      (let [turn      (bridge/begin-turn! "exec-cancel")
            started?  (promise)
            result    (future
                        (with-redefs [sut/start-process (fn [_]
                                                         (deliver started? true)
                                                         ::proc)
                                      sut/process-finished? (fn [_ _] false)
                                      sut/destroy-process! (fn [_] nil)]
                          (sut/exec-tool {:command "ignored" :session-key "exec-cancel"}))) ]
        @started?
        (bridge/cancel! "exec-cancel")
        (should= :cancelled (:error (deref result 1000 nil)))
        (bridge/end-turn! "exec-cancel" turn))))

  ;; endregion ^^^^^ exec ^^^^^

  ;; region ----- registration -----

  (describe "register-all!"

    (it "registers only the explicitly allowed tools when an allow list is provided"
      (sut/register-all! registry/register! #{"read" "write"})
      (should= #{"read" "write"} (set (map :name (registry/all-tools)))))

    (it "fails fast when registering grep without rg on path"
      (with-redefs [shell/cmd-available? (constantly false)]
        (should-throw clojure.lang.ExceptionInfo
                      (sut/register-all! registry/register! #{"grep"}))))

    (it "registers glob when it is allowed"
      (sut/register-all! registry/register! #{"glob"})
      (should= #{"glob"} (set (map :name (registry/all-tools))))))

  ;; endregion ^^^^^ registration ^^^^^

  )
