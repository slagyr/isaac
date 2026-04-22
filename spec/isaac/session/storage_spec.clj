(ns isaac.session.storage-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.fs :as fs]
    [isaac.session.storage :as sut]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def test-dir "/test/storage")
(def test-key "agent:main:cli:direct:user1")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "Session Storage"

  (before (clean-dir! test-dir))
  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  ;; region ----- parse-key -----

  (describe "parse-key"

    (it "parses a standard session key"
      (let [result (sut/parse-key "agent:main:cli:direct:user1")]
        (should= "main" (:agent result))
        (should= "cli" (:channel result))
        (should= "direct" (:chatType result))
        (should= "user1" (:conversation result))))

    (it "parses a short 3-part key (agent:id:conversation)"
      (let [result (sut/parse-key "agent:main:main")]
        (should= "main" (:agent result))
        (should= "cli" (:channel result))
        (should= "direct" (:chatType result))
        (should= "main" (:conversation result))))

    (it "returns nil for too-short key"
      (should-be-nil (sut/parse-key "agent:main"))))

  ;; endregion ^^^^^ parse-key ^^^^^

  ;; region ----- create-session! -----

  (describe "create-session!"

    (it "creates a new session with index and transcript"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= test-key (:key entry))
        (should (string? (:sessionId entry)))
        (should (string? (:sessionFile entry)))
        (should= "cli" (:channel entry))
        (should= "direct" (:chatType entry))
        (should= 0 (:compactionCount entry))
        (should= 0 (:inputTokens entry))
        (should= 0 (:outputTokens entry))
        (should= 0 (:totalTokens entry))))

    (it "stores cwd in the index entry"
      (let [entry (sut/create-session! test-dir test-key)]
        (should (string? (:cwd entry)))
        (should (not (clojure.string/blank? (:cwd entry))))))

    (it "defaults origin to cli when none is provided"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= {:kind :cli} (:origin entry))))

    (it "stores an explicit origin in the index entry"
      (let [entry (sut/create-session! test-dir test-key {:origin {:kind :cron :name "health-check"}})]
        (should= {:kind :cron :name "health-check"} (:origin entry))))

    (it "writes a session header to the transcript"
      (sut/create-session! test-dir test-key)
      (let [transcript (sut/get-transcript test-dir test-key)]
        (should= 1 (count transcript))
        (should= "session" (:type (first transcript)))))

    (it "resumes an existing session instead of creating a duplicate"
      (let [first  (sut/create-session! test-dir test-key)
            second (sut/create-session! test-dir test-key)]
        (should= (:sessionId first) (:sessionId second))
        (should= 1 (count (sut/list-sessions test-dir "main")))))

    (it "creates a fresh session when the index entry exists but its transcript is missing"
      (let [first  (sut/create-session! test-dir test-key)
             _      (fs/delete (str test-dir "/sessions/" (:sessionFile first)))
             second (sut/create-session! test-dir test-key)]
        (should-not= (:sessionId first) (:sessionId second))
        (should= 1 (count (sut/list-sessions test-dir "main")))))

    (it "uses sequential names for unnamed sessions when configured"
      (with-redefs [config/load-config (fn [& _] {:sessions {:naming-strategy :sequential}})]
        (let [first  (sut/create-session! test-dir nil)
              second (sut/create-session! test-dir nil)]
          (should= "session-1" (:name first))
          (should= "session-2" (:name second)))))

    (it "persists the sequential counter across unnamed creates"
      (with-redefs [config/load-config (fn [& _] {:sessions {:naming-strategy :sequential}})]
        (sut/create-session! test-dir nil)
        (should= "1" (str/trim (fs/slurp (str test-dir "/sessions/.counter"))))
        (let [entry (sut/create-session! test-dir nil)]
          (should= "session-2" (:name entry))
          (should= "2" (str/trim (fs/slurp (str test-dir "/sessions/.counter")))))))

    (it "prefers an explicit name over the configured sequential strategy"
      (with-redefs [config/load-config (fn [& _] {:sessions {:naming-strategy :sequential}})]
        (let [entry (sut/create-session! test-dir "friday-debug")]
          (should= "friday-debug" (:name entry))
          (should= nil (sut/get-session test-dir "session-1"))))))

  ;; endregion ^^^^^ create-session! ^^^^^

  ;; region ----- list-sessions -----

  (describe "list-sessions"

    (it "returns empty when no sessions"
      (should= [] (sut/list-sessions test-dir "main")))

    (it "lists created sessions"
      (sut/create-session! test-dir test-key)
      (sut/create-session! test-dir "agent:main:cli:direct:user2")
      (should= 2 (count (sut/list-sessions test-dir "main"))))

    (it "reads a flat EDN index keyed by session id"
      (let [entry      (sut/create-session! test-dir "Friday Debug!")
            index-path (str test-dir "/sessions/index.edn")
            index-map  (edn/read-string (fs/slurp index-path))]
        (should (contains? index-map "friday-debug"))
        (should= "Friday Debug!" (get-in index-map ["friday-debug" :name]))
        (should= (:sessionFile entry) (get-in index-map ["friday-debug" :sessionFile])))))

  ;; endregion ^^^^^ list-sessions ^^^^^

  ;; region ----- append-message! -----

  (describe "append-message!"

    (it "appends a message to the transcript"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
      (let [transcript (sut/get-transcript test-dir test-key)]
        (should= 2 (count transcript))
        (should= "message" (:type (second transcript)))
        (should= "user" (get-in (second transcript) [:message :role]))))

    (it "links entries via parentId"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "First"})
      (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
      (let [transcript (sut/get-transcript test-dir test-key)
            header     (first transcript)
            msg1       (second transcript)
            msg2       (nth transcript 2)]
        (should= (:id header) (:parentId msg1))
        (should= (:id msg1) (:parentId msg2))))

    (it "updates lastChannel and lastTo on routing messages"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hi" :channel "telegram" :to "bob"})
      (let [listing (sut/list-sessions test-dir "main")
            entry   (first listing)]
        (should= "telegram" (:lastChannel entry))
        (should= "bob" (:lastTo entry)))))

  ;; endregion ^^^^^ append-message! ^^^^^

  ;; region ----- append-error! -----

  (describe "append-error!"

    (it "stores errors as type error entries"
      (sut/create-session! test-dir test-key)
      (sut/append-error! test-dir test-key {:content "something went wrong"
                                            :error   ":connection-refused"
                                            :model   "echo"
                                            :provider "grover"})
      (let [transcript (sut/get-transcript test-dir test-key)
            last-entry (last transcript)]
        (should= "error" (:type last-entry))
        (should= "something went wrong" (:content last-entry))
        (should= ":connection-refused" (:error last-entry))
        (should= "echo" (:model last-entry))
        (should= "grover" (:provider last-entry)))))

  ;; endregion ^^^^^ append-error! ^^^^^

  ;; region ----- update-session! -----

  (describe "update-session!"

    (it "updates arbitrary fields on the index entry"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:inputTokens 42})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 42 (:inputTokens entry))))

    (it "normalizes updatedAt to ISO timestamp"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:updatedAt 1000})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should (string? (:updatedAt entry)))
        (should (re-find #"^\d{4}-\d{2}-\d{2}T" (:updatedAt entry))))))

  ;; endregion ^^^^^ update-session! ^^^^^

  ;; region ----- append-compaction! -----

  (describe "append-compaction!"

    (it "appends a compaction entry and increments count"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
      (let [transcript (sut/get-transcript test-dir test-key)
            last-id    (:id (last transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "A summary" :firstKeptEntryId last-id :tokensBefore 100})
        (let [updated-transcript (sut/get-transcript test-dir test-key)
              compaction         (last updated-transcript)
              listing            (sut/list-sessions test-dir "main")
              entry              (first listing)]
          (should= "compaction" (:type compaction))
          (should= "A summary" (:summary compaction))
          (should= 1 (:compactionCount entry))))))

  ;; endregion ^^^^^ append-compaction! ^^^^^

  ;; region ----- splice-compaction! -----

  (describe "splice-compaction!"

    (it "replaces compacted entries in place and preserves later entries"
      (sut/create-session! test-dir test-key)
      (let [session-id    (:id (first (sut/get-transcript test-dir test-key)))
            first-msg     (sut/append-message! test-dir test-key {:role "user" :content "First"})
            second-msg    (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
            third-msg     (sut/append-message! test-dir test-key {:role "user" :content "Third"})
            fourth-msg    (sut/append-message! test-dir test-key {:role "assistant" :content "Fourth"})
            later-msg     (sut/append-message! test-dir test-key {:role "user" :content "Later"})]
        (sut/splice-compaction! test-dir test-key
                                {:summary          "Summary"
                                 :firstKeptEntryId (:id third-msg)
                                 :tokensBefore     50
                                 :compactedEntryIds [(:id first-msg) (:id second-msg)]})
        (let [result           (sut/get-transcript test-dir test-key)
              compaction       (nth result 1)
              kept-msg         (nth result 2)
              kept-assistant   (nth result 3)
              surviving-msg    (nth result 4)]
          (should= 5 (count result))
          (should= "session" (:type (first result)))
          (should= "compaction" (:type compaction))
          (should= session-id (:parentId compaction))
          (should= "message" (:type kept-msg))
          (should= [{:type "text" :text "Third"}] (get-in kept-msg [:message :content]))
          (should= (:id compaction) (:parentId kept-msg))
          (should= (:id third-msg) (:id kept-msg))
          (should= (:id fourth-msg) (:id kept-assistant))
          (should= (:id later-msg) (:id surviving-msg))))

    (it "reparents surviving entries whose parent was compacted"
      (sut/create-session! test-dir test-key)
      (let [session-id (:id (first (sut/get-transcript test-dir test-key)))
            first-msg  (sut/append-message! test-dir test-key {:role "user" :content "First"})
            second-msg (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
            later-msg  (sut/append-message! test-dir test-key {:role "user" :content "Later"})]
        (sut/splice-compaction! test-dir test-key
                                {:summary          "Summary"
                                 :firstKeptEntryId nil
                                 :tokensBefore     50
                                 :compactedEntryIds [(:id first-msg) (:id second-msg)]})
        (let [result     (sut/get-transcript test-dir test-key)
              compaction (nth result 1)
              kept-msg   (nth result 2)]
          (should= 3 (count result))
          (should= session-id (:parentId compaction))
          (should= (:id compaction) (:parentId kept-msg))
          (should= (:id later-msg) (:id kept-msg)))))

    (it "preserves prior compaction entries during repeated compaction"
      (sut/create-session! test-dir test-key)
      (let [session-id  (:id (first (sut/get-transcript test-dir test-key)))
            first-msg   (sut/append-message! test-dir test-key {:role "user" :content "First"})
            second-msg  (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
            compaction1 (sut/append-compaction! test-dir test-key
                                               {:summary          "Summary one"
                                                :firstKeptEntryId nil
                                                :tokensBefore     50})
            _           (sut/truncate-after-compaction! test-dir test-key)
            third-msg   (sut/append-message! test-dir test-key {:role "user" :content "Third"})]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Summary two"
                                 :firstKeptEntryId  nil
                                 :tokensBefore      60
                                 :compactedEntryIds [(:id compaction1) (:id third-msg)]})
        (let [result         (sut/get-transcript test-dir test-key)
              first-summary  (nth result 1)
              second-summary (nth result 2)]
          (should= 3 (count result))
          (should= session-id (:parentId first-summary))
          (should= "Summary one" (:summary first-summary))
          (should= (:id first-summary) (:parentId second-summary))
          (should= "Summary two" (:summary second-summary))))))

  ;; endregion ^^^^^ splice-compaction! ^^^^^

  ;; region ----- truncate-after-compaction! -----

  (describe "truncate-after-compaction!"

    (it "returns nil when no compaction entry exists"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
      (should-be-nil (sut/truncate-after-compaction! test-dir test-key)))

    (it "removes all message entries before compaction when firstKeptEntryId is nil"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "First"})
      (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
      (let [transcript (sut/get-transcript test-dir test-key)
            last-id    (:id (last transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "All summarized" :firstKeptEntryId nil :tokensBefore 50})
        (sut/append-message! test-dir test-key {:role "user" :content "New question"})
        (sut/truncate-after-compaction! test-dir test-key)
        (let [result (sut/get-transcript test-dir test-key)]
          (should= 3 (count result))
          (should= "session" (:type (nth result 0)))
          (should= "compaction" (:type (nth result 1)))
          (should= "message" (:type (nth result 2))))))

    (it "removes message entries before firstKeptEntryId"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "First"})
      (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
      (sut/append-message! test-dir test-key {:role "user" :content "Third"})
      (let [transcript    (sut/get-transcript test-dir test-key)
            third-msg-id  (:id (last transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "Partial summary" :firstKeptEntryId third-msg-id :tokensBefore 50})
        (sut/append-message! test-dir test-key {:role "user" :content "New question"})
        (sut/truncate-after-compaction! test-dir test-key)
        (let [result (sut/get-transcript test-dir test-key)]
          (should= 4 (count result))
          (should= "session" (:type (nth result 0)))
          (should= "message" (:type (nth result 1)))
          (should= [{:type "text" :text "Third"}] (get-in (nth result 1) [:message :content]))
          (should= "compaction" (:type (nth result 2)))
          (should= "message" (:type (nth result 3))))))

    (it "reparents the first kept message to the session header"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "First"})
      (sut/append-message! test-dir test-key {:role "user" :content "Second"})
      (let [transcript   (sut/get-transcript test-dir test-key)
            second-id    (:id (last transcript))
            session-id   (:id (first transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "Summary" :firstKeptEntryId second-id :tokensBefore 50})
        (sut/truncate-after-compaction! test-dir test-key)
        (let [result     (sut/get-transcript test-dir test-key)
              kept-msg   (nth result 1)]
          (should= session-id (:parentId kept-msg)))))

    (it "returns nil when no entries were removed"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Only message"})
      (let [transcript (sut/get-transcript test-dir test-key)
            msg-id     (:id (last transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "Summary" :firstKeptEntryId msg-id :tokensBefore 50})
        (should-be-nil (sut/truncate-after-compaction! test-dir test-key)))))

  ;; endregion ^^^^^ truncate-after-compaction! ^^^^^

  ;; region ----- update-tokens! -----

  (describe "update-tokens!"

    (it "accumulates token counts"
      (sut/create-session! test-dir test-key)
      (sut/update-tokens! test-dir test-key {:inputTokens 10 :outputTokens 5})
      (sut/update-tokens! test-dir test-key {:inputTokens 20 :outputTokens 15})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 30 (:inputTokens entry))
        (should= 20 (:outputTokens entry))
        (should= 50 (:totalTokens entry))))

    (it "tracks cache tokens when provided"
      (sut/create-session! test-dir test-key)
      (sut/update-tokens! test-dir test-key {:inputTokens 10 :outputTokens 5 :cacheRead 3 :cacheWrite 2})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 3 (:cacheRead entry))
        (should= 2 (:cacheWrite entry)))))

  ;; endregion ^^^^^ update-tokens! ^^^^^

  ;; region ----- Logging -----

  (describe "session lifecycle logging"

    (helper/with-captured-logs)

    (it "logs session creation"
      (sut/create-session! test-dir test-key)
      (should (some #(= :session/created (:event %)) @log/captured-logs)))

    (it "logs session resume when session already exists"
      (sut/create-session! test-dir test-key)
      (sut/create-session! test-dir test-key)
      (should (some #(= :session/opened (:event %)) @log/captured-logs)))

    )

  ;; endregion ^^^^^ Logging ^^^^^

  ))
