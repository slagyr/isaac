(ns isaac.cli.sessions-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [speclj.core :refer :all]
    [isaac.cli.sessions :as sessions]
    [isaac.session.storage :as storage]))

(defn- delete-dir! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))))

(describe "sessions/format-age"
  (it "formats seconds as 'Xs ago'"
    (should= "30s ago" (sessions/format-age 30000)))

  (it "formats minutes as 'Xm ago'"
    (should= "5m ago" (sessions/format-age (* 5 60 1000))))

  (it "formats hours as 'Xh ago'"
    (should= "2h ago" (sessions/format-age (* 2 60 60 1000))))

  (it "formats days as 'Xd ago'"
    (should= "3d ago" (sessions/format-age (* 3 24 60 60 1000)))))

(describe "sessions/format-context"
  (it "formats tokens and context window with percentage"
    (should= "5,000 / 32,768 (15%)" (sessions/format-context 5000 32768)))

  (it "handles zero tokens"
    (should= "0 / 32,768 (0%)" (sessions/format-context 0 32768)))

  (it "handles nil tokens as zero"
    (should= "0 / 32,768 (0%)" (sessions/format-context nil 32768))))

(describe "sessions/list-all"
  (with-all state-dir "target/test-state/sessions-spec")

  (before-all
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "agent:main:acp:direct:abc")
    (storage/update-session! @state-dir "agent:main:acp:direct:abc"
                             {:totalTokens 5000 :updatedAt "2026-04-12T15:00:00"})
    (storage/create-session! @state-dir "agent:ketch:acp:direct:ghi")
    (storage/update-session! @state-dir "agent:ketch:acp:direct:ghi"
                             {:totalTokens 12000 :updatedAt "2026-04-11T10:00:00"}))

  (it "returns a map of agent-id to sessions list"
    (let [result (sessions/list-all @state-dir nil)]
      (should (contains? result "main"))
      (should (contains? result "ketch"))))

  (it "each agent entry is a non-empty seq of sessions"
    (let [result (sessions/list-all @state-dir nil)]
      (should (seq (get result "main")))
      (should (seq (get result "ketch")))))

  (it "filters to one agent when agent-filter is provided"
    (let [result (sessions/list-all @state-dir "ketch")]
      (should (contains? result "ketch"))
      (should-not (contains? result "main")))))

(describe "sessions/run"
  (with-all state-dir "target/test-state/sessions-run-spec")

  (before-all
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "agent:main:acp:direct:abc")
    (storage/update-session! @state-dir "agent:main:acp:direct:abc"
                             {:totalTokens 5000 :updatedAt "2026-04-12T15:00:00"}))

  (it "outputs agent header"
    (let [output (with-out-str (sessions/run {:state-dir @state-dir}))]
      (should (str/includes? output "agent: main"))))

  (it "outputs session key"
    (let [output (with-out-str (sessions/run {:state-dir @state-dir}))]
      (should (str/includes? output "agent:main:acp:direct:abc"))))

  (it "returns exit code 0"
    (let [result (atom nil)]
      (with-out-str (reset! result (sessions/run {:state-dir @state-dir})))
      (should= 0 @result)))

  (it "returns exit code 0 when no sessions exist"
    (let [empty-dir "target/test-state/sessions-empty"
          _         (delete-dir! empty-dir)
          result    (atom nil)]
      (with-out-str (reset! result (sessions/run {:state-dir empty-dir})))
      (should= 0 @result)))

  (it "prints 'no sessions' when no sessions exist"
    (let [empty-dir "target/test-state/sessions-empty2"
          _         (delete-dir! empty-dir)
          output    (with-out-str (sessions/run {:state-dir empty-dir}))]
      (should (str/includes? output "no sessions"))))

  (it "returns exit code 1 for unknown agent"
    (let [result (atom nil)]
      (with-out-str (reset! result (sessions/run {:state-dir @state-dir :agent "nonexistent"})))
      (should= 1 @result)))

  (it "prints error to stderr for unknown agent"
    (let [err-writer (java.io.StringWriter.)]
      (binding [*err* (java.io.PrintWriter. err-writer)]
        (with-out-str
          (sessions/run {:state-dir @state-dir :agent "nonexistent"})))
      (let [stderr (str err-writer)]
        (should (str/includes? stderr "unknown agent"))
        (should (str/includes? stderr "nonexistent"))))))
