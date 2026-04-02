(ns isaac.session.storage-spec
  (:require
    [clojure.java.io :as io]
    [isaac.session.storage :as sut]
    [speclj.core :refer :all]))

(def test-dir "target/test-storage")
(def test-key "agent:main:cli:direct:user1")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "Session Storage"

  (before (clean-dir! test-dir))

  ;; region ----- parse-key -----

  (describe "parse-key"

    (it "parses a standard session key"
      (let [result (sut/parse-key "agent:main:cli:direct:user1")]
        (should= "main" (:agent result))
        (should= "cli" (:channel result))
        (should= "direct" (:chatType result))
        (should= "user1" (:conversation result))))

    (it "returns nil for too-short key"
      (should-be-nil (sut/parse-key "agent:main:cli"))))

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

    (it "writes a session header to the transcript"
      (sut/create-session! test-dir test-key)
      (let [transcript (sut/get-transcript test-dir test-key)]
        (should= 1 (count transcript))
        (should= "session" (:type (first transcript)))))

    (it "resumes an existing session instead of creating a duplicate"
      (let [first  (sut/create-session! test-dir test-key)
            second (sut/create-session! test-dir test-key)]
        (should= (:sessionId first) (:sessionId second))
        (should= 1 (count (sut/list-sessions test-dir "main"))))))

  ;; endregion ^^^^^ create-session! ^^^^^

  ;; region ----- list-sessions -----

  (describe "list-sessions"

    (it "returns empty when no sessions"
      (should= [] (sut/list-sessions test-dir "main")))

    (it "lists created sessions"
      (sut/create-session! test-dir test-key)
      (sut/create-session! test-dir "agent:main:cli:direct:user2")
      (should= 2 (count (sut/list-sessions test-dir "main")))))

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

  ;; region ----- update-session! -----

  (describe "update-session!"

    (it "updates arbitrary fields on the index entry"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:updatedAt 42})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 42 (:updatedAt entry)))))

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

  )
