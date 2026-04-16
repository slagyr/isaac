(ns isaac.tool.builtin-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.session.bridge :as bridge]
    [isaac.tool.builtin :as sut]
    [isaac.tool.registry :as registry]
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
  (spit (str test-dir "/" name) content))

(defn- read-file [name]
  (slurp (str test-dir "/" name)))

(describe "Built-in Tools"

  (before (clean!))
  (after (registry/clear!))

  ;; region ----- read -----

  (describe "read"

    (it "returns file contents"
      (write-file! "hello.txt" "Hello, world!")
      (let [result (sut/read-tool {:filePath (str test-dir "/hello.txt")})]
        (should= "Hello, world!" (:result result))
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
        (should-not (str/includes? (:result result) "line 15")))))

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
        (should (string? (:result result))))))

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
      (should= #{"read" "write"} (set (map :name (registry/all-tools))))))

  ;; endregion ^^^^^ registration ^^^^^

  )
