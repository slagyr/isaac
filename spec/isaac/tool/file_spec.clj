(ns isaac.tool.file-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.file :as sut]
    [isaac.tool.support :as support]
    [speclj.core :refer :all]))

(describe "File tools"

  (before (support/clean!))

  (around [it]
    (helper/with-memory-store
      (system/with-system {:state-dir support/test-dir}
        (it))))

  (describe "read"

    (it "returns file contents with line-number prefixes"
      (support/write-file! "hello.txt" "Hello, world!")
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/hello.txt")})]
        (should= "1: Hello, world!" (:result result))
        (should-be-nil (:isError result))))

    (it "returns multi-line file contents"
      (support/write-file! "multi.txt" "line one\nline two\nline three")
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/multi.txt")})]
        (should (str/includes? (:result result) "line one"))
        (should (str/includes? (:result result) "line three"))))

    (it "returns error for missing file"
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/no-such-file.txt")})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "lists directory contents"
      (.mkdirs (io/file (str support/test-dir "/mydir")))
      (support/write-file! "mydir/a.txt" "a")
      (support/write-file! "mydir/b.txt" "b")
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/mydir")})]
        (should (str/includes? (:result result) "a.txt"))
        (should (str/includes? (:result result) "b.txt"))))

    (it "respects offset to skip leading lines"
      (support/write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/numbered.txt") "offset" 10})]
        (should-not (str/includes? (:result result) "line 9"))
        (should (str/includes? (:result result) "line 10"))))

    (it "respects limit to cap returned lines"
      (support/write-file! "numbered.txt" (str/join "\n" (map #(str "line " %) (range 1 21))))
      (let [result (sut/read-tool {"file_path" (str support/test-dir "/numbered.txt") "offset" 10 "limit" 5})]
        (should (str/includes? (:result result) "line 10"))
        (should (str/includes? (:result result) "line 14"))
        (should-not (str/includes? (:result result) "line 15"))))

    (it "allows reading within the crew quarters"
      (let [state-dir   support/test-dir
            quarters    (str state-dir "/crew/main")
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file quarters))
        (spit (str quarters "/notes.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str quarters "/notes.txt")
                                       "session_key" session-key}))]
          (should= "1: hello" (:result result)))))

    (it "allows reading within explicit whitelisted directories"
      (let [state-dir   support/test-dir
            session-key "main-session"
            whitelisted (str support/test-dir "/playground")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (.mkdirs (io/file whitelisted))
        (spit (str whitelisted "/data.txt") "hello")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                                :crew {"main" {:tools {:allow ["read"]
                                                                                         :directories [whitelisted]}}}
                                                                :models {}
                                                                :providers {}})]
                       (sut/read-tool {"file_path"   (str whitelisted "/data.txt")
                                       "session_key" session-key}))]
          (should= "1: hello" (:result result)))))

    (it "rejects reading outside allowed directories"
      (let [state-dir   support/test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   "/etc/passwd"
                                       "session_key" session-key}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "allows reading in session cwd only with :cwd opt in"
      (let [state-dir   support/test-dir
            session-key "main-session"
            cwd         (str support/test-dir "/project")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {}
                                                                :crew {"main" {:tools {:allow ["read"]
                                                                                         :directories [:cwd]}}}
                                                                :models {}
                                                                :providers {}})]
                       (sut/read-tool {"file_path"   (str cwd "/hello.txt")
                                       "session_key" session-key}))]
          (should= "1: hi there" (:result result)))))

    (it "rejects reading the session cwd without :cwd opt in"
      (let [state-dir   support/test-dir
            session-key "main-session"
            cwd         (str support/test-dir "/project")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd cwd})
        (.mkdirs (io/file cwd))
        (spit (str cwd "/hello.txt") "hi there")
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str cwd "/hello.txt")
                                       "session_key" session-key}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects path traversal that escapes the quarters"
      (let [state-dir   support/test-dir
            session-key "main-session"
            quarters    (str state-dir "/crew/main")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str quarters "/../../etc/passwd")
                                       "session_key" session-key}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result))))))

    (it "rejects reading the config directory"
      (let [state-dir   support/test-dir
            session-key "main-session"]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["read"]}}} :models {} :providers {}})]
                       (sut/read-tool {"file_path"   (str state-dir "/config/crew/main.edn")
                                       "session_key" session-key}))]
          (should (:isError result))
          (should (re-find #"path outside allowed directories" (:error result)))))))

  (describe "write"

    (it "creates a new file with the given content"
      (let [path   (str support/test-dir "/new.txt")
            result (sut/write-tool {"file_path" path "content" "hello world"})]
        (should-be-nil (:isError result))
        (should= "hello world" (slurp path))))

    (it "overwrites an existing file"
      (support/write-file! "existing.txt" "old content")
      (sut/write-tool {"file_path" (str support/test-dir "/existing.txt") "content" "new content"})
      (should= "new content" (support/read-file "existing.txt")))

    (it "creates parent directories if needed"
      (let [path   (str support/test-dir "/sub/dir/file.txt")
            result (sut/write-tool {"file_path" path "content" "deep"})]
        (should-be-nil (:isError result))
        (should= "deep" (slurp path))))

    (it "returns a success message"
      (let [result (sut/write-tool {"file_path" (str support/test-dir "/ok.txt") "content" "ok"})]
        (should (string? (:result result)))))

    (it "auto-creates the crew quarters on first use"
      (let [state-dir   support/test-dir
            session-key "main-session"
            path        (str state-dir "/crew/main/new.txt")]
        (helper/create-session! state-dir session-key {:crew "main" :cwd "/work/project"})
        (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["write"]}}} :models {} :providers {}})]
                       (sut/write-tool {"file_path"   path
                                        "content"     "hello"
                                        "session_key" session-key}))]
          (should= "hello" (slurp path))
          (should (string? (:result result))))))

    (it "rejects writes outside allowed directories"
      (let [state-dir   support/test-dir
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

  (describe "edit"

    (it "replaces matching text"
      (support/write-file! "code.txt" "foo = 1\nbar = 2")
      (let [result (sut/edit-tool {"file_path"  (str support/test-dir "/code.txt")
                                   "old_string" "foo = 1"
                                   "new_string" "foo = 42"})]
        (should-be-nil (:isError result))
        (should= "foo = 42\nbar = 2" (support/read-file "code.txt"))))

    (it "returns error when string not found"
      (support/write-file! "code.txt" "foo = 1")
      (let [result (sut/edit-tool {"file_path"  (str support/test-dir "/code.txt")
                                   "old_string" "not here"
                                   "new_string" "replacement"})]
        (should (:isError result))
        (should (re-find #"not found" (:error result)))))

    (it "returns error when multiple matches and replace_all not set"
      (support/write-file! "code.txt" "x = 1\nx = 1\nx = 1")
      (let [result (sut/edit-tool {"file_path"  (str support/test-dir "/code.txt")
                                   "old_string" "x = 1"
                                   "new_string" "x = 2"})]
        (should (:isError result))
        (should (re-find #"multiple" (:error result)))))

    (it "replaces all occurrences when replace_all is true"
      (support/write-file! "code.txt" "x = 1\ny = 2\nx = 1")
      (let [result (sut/edit-tool {"file_path"   (str support/test-dir "/code.txt")
                                   "old_string"  "x = 1"
                                   "new_string"  "x = 99"
                                   "replace_all" true})]
        (should-be-nil (:isError result))
        (should= "x = 99\ny = 2\nx = 99" (support/read-file "code.txt"))))

    (it "returns error for missing file"
      (let [result (sut/edit-tool {"file_path"  (str support/test-dir "/missing.txt")
                                   "old_string" "x"
                                   "new_string" "y"})]
        (should (:isError result)))))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (describe "path resolution against session cwd"

    (with session-key "res-session")
    (with cwd (str support/test-dir "/crew/main/workspace"))

    (before
      (.mkdirs (io/file @cwd))
      (helper/create-session! support/test-dir @session-key {:crew "main" :cwd @cwd}))

    (it "read resolves '.' to session cwd"
      (spit (str @cwd "/marker.txt") "found")
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
                     (sut/read-tool {"file_path" "." "session_key" @session-key}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "marker.txt"))))

    (it "read resolves an empty file_path to session cwd"
      (spit (str @cwd "/marker.txt") "found")
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
                     (sut/read-tool {"file_path" "" "session_key" @session-key}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "marker.txt"))))

    (it "read resolves a relative file_path against session cwd"
      (spit (str @cwd "/hello.txt") "relative content")
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
                     (sut/read-tool {"file_path" "hello.txt" "session_key" @session-key}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "relative content"))))

    (it "write resolves a relative file_path against session cwd"
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
                     (sut/write-tool {"file_path" "out.txt" "content" "written" "session_key" @session-key}))]
        (should-be-nil (:isError result))
        (should= "written" (slurp (str @cwd "/out.txt")))))

    (it "edit resolves a relative file_path against session cwd"
      (spit (str @cwd "/target.txt") "original")
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
                     (sut/edit-tool {"file_path" "target.txt" "old_string" "original"
                                     "new_string" "updated" "session_key" @session-key}))]
        (should-be-nil (:isError result))
        (should= "updated" (slurp (str @cwd "/target.txt")))))))
