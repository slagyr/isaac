(ns isaac.session.store.index-impl-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.impl-common :as c]
    [isaac.session.store.index :as sut]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(def test-dir "/test/index-storage")
(def test-key "user1")

(defn- index-path []
  (str test-dir "/sessions/index.edn"))

(defn- read-index []
  (edn/read-string (fs/slurp- (system/get :fs) (index-path))))

(describe "Index Session Storage"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (system/with-system {:fs (fs/mem-fs)}
      (example)))

  ;; region ----- create-session! -----

  (describe "create-session!"

    (it "creates a new session and writes to the combined index"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= "user1" (:key entry))
        (should (string? (:sessionId entry)))
        (should (string? (:session-file entry)))
        (should= :retain (:history-retention entry))
        (should= 0 (:compaction-count entry))
        (should= 0 (:input-tokens entry))
        (should= 0 (:output-tokens entry))
        (should= 0 (:total-tokens entry))
        (should (fs/exists?- (system/get :fs) (index-path)))))

    (it "index contains the session entry"
      (sut/create-session! test-dir test-key)
      (let [index (read-index)]
        (should (contains? index "user1"))
        (should= "user1" (get-in index ["user1" :id]))))

    (it "stores multiple sessions in a single index file"
      (sut/create-session! test-dir "chat-1")
      (sut/create-session! test-dir "chat-2")
      (let [index (read-index)]
        (should= 2 (count index))
        (should (contains? index "chat-1"))
        (should (contains? index "chat-2"))))

    (it "does not create per-session sidecar .edn files"
      (sut/create-session! test-dir test-key)
      (let [dir      (str test-dir "/sessions")
            edn-files (->> (or (fs/children- (system/get :fs) dir) [])
                            (filter #(str/ends-with? % ".edn")))]
        (should= ["index.edn"] edn-files)))

    (it "writes session metadata with kebab-case schema keys"
      (sut/create-session! test-dir test-key {:chatType "direct"})
      (let [entry (get (read-index) "user1")]
        (should= "direct" (:chat-type entry))
        (should-not (contains? entry :chatType))))

    (it "stores an explicit history-retention override"
      (let [entry (sut/create-session! test-dir test-key {:history-retention :prune})]
        (should= :prune (:history-retention entry))))

    (it "resumes an existing session"
      (let [first  (sut/create-session! test-dir test-key)
            second (sut/create-session! test-dir test-key)]
        (should= (:sessionId first) (:sessionId second))
        (should= 1 (count (sut/list-sessions test-dir "main")))))

    (it "rejects a different name that slug-collides with an existing session"
      (sut/create-session! test-dir "friday-debug")
      (let [error (try
                    (sut/create-session! test-dir "Friday Debug")
                    nil
                    (catch clojure.lang.ExceptionInfo e e))]
        (should-not-be-nil error)
        (should= "session already exists: friday-debug" (ex-message error))))

    (it "creates a fresh session when its transcript is missing"
      (let [first  (sut/create-session! test-dir test-key)
            _      (fs/delete- (system/get :fs) (str test-dir "/sessions/" (:session-file first)))
            second (sut/create-session! test-dir test-key)]
        (should-not= (:sessionId first) (:sessionId second))
        (should= 1 (count (sut/list-sessions test-dir "main")))))

    (it "uses sequential names for unnamed sessions when configured"
      (with-redefs [config/load-config (fn [& _] {:sessions {:naming-strategy :sequential}})]
        (let [first  (sut/create-session! test-dir nil)
              second (sut/create-session! test-dir nil)]
          (should= "session-1" (:name first))
          (should= "session-2" (:name second))))))

  ;; endregion ^^^^^ create-session! ^^^^^

  ;; region ----- list-sessions -----

  (describe "list-sessions"

    (it "returns empty when no sessions"
      (should= [] (sut/list-sessions test-dir "main")))

    (it "lists all created sessions"
      (sut/create-session! test-dir test-key)
      (sut/create-session! test-dir "user2")
      (should= 2 (count (sut/list-sessions test-dir "main"))))

    (it "filters sessions by crew"
      (sut/create-session! test-dir "chat-a" {:crew "alpha"})
      (sut/create-session! test-dir "chat-b" {:crew "beta"})
      (should= 1 (count (sut/list-sessions test-dir "alpha")))
      (should= 1 (count (sut/list-sessions test-dir "beta")))))

  ;; endregion ^^^^^ list-sessions ^^^^^

  ;; region ----- migrate from sidecars -----

  (describe "migration from sidecar format"

    (it "imports existing sidecar entries into the index on first read"
      (let [sidecar-content {:id "chat-1" :name "Chat 1" :session-file "chat-1.jsonl"
                              :crew "main" :updated-at "2026-05-10T10:00:00"}
            sessions-dir    (str test-dir "/sessions")]
        (fs/mkdirs- (system/get :fs) sessions-dir)
        (fs/spit-   (system/get :fs) (str sessions-dir "/chat-1.edn")
                 (binding [*print-namespace-maps* false]
                   (with-out-str (clojure.pprint/pprint sidecar-content))))
        (fs/spit- (system/get :fs) (str sessions-dir "/chat-1.jsonl")
                 (str (json/generate-string {:type "session" :id "abc12345"
                                              :timestamp "2026-05-10T10:00:00"
                                              :version 3 :cwd test-dir}) "\n"))
        (let [sessions (sut/list-sessions test-dir "main")]
          (should= 1 (count sessions))
          (should= "chat-1" (:id (first sessions)))
          (should (fs/exists?- (system/get :fs) (index-path)))))))

  ;; endregion ^^^^^ migrate from sidecars ^^^^^

  ;; region ----- append-message! -----

  (describe "append-message!"

    (it "appends a message to the transcript"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
      (let [transcript (sut/get-transcript test-dir test-key)]
        (should= 2 (count transcript))
        (should= "message" (:type (second transcript)))))

    (it "updates updated-at in the index"
      (let [counter (atom 0)]
        (with-redefs [sut/now-iso (fn [] (format "2026-01-01T00:00:00.%03d" (swap! counter inc)))]
          (sut/create-session! test-dir test-key)
          (let [before (:updated-at (sut/get-session test-dir test-key))]
            (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
            (let [after (:updated-at (sut/get-session test-dir test-key))]
              (should-not= before after)))))))

  ;; endregion ^^^^^ append-message! ^^^^^

  ;; region ----- update-session! -----

  (describe "update-session!"

    (it "updates the index entry"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:input-tokens 100 :output-tokens 50})
      (let [entry (sut/get-session test-dir test-key)]
        (should= 100 (:input-tokens entry))
        (should= 50 (:output-tokens entry)))))

  ;; endregion ^^^^^ update-session! ^^^^^

  ;; region ----- splice-compaction! -----

  (describe "splice-compaction!"

    (it "retains compacted entries on disk and exposes an active transcript view"
      (sut/create-session! test-dir test-key {:history-retention :retain})
      (let [first-msg  (sut/append-message! test-dir test-key {:role "user" :content "First"})
            second-msg (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
            third-msg  (sut/append-message! test-dir test-key {:role "user" :content "Third"})]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Summary"
                                 :firstKeptEntryId  (:id third-msg)
                                 :tokensBefore      20
                                 :compactedEntryIds [(:id first-msg) (:id second-msg)]})
        (let [transcript (sut/get-transcript test-dir test-key)
              active     (sut/active-transcript test-dir test-key)
              session    (sut/get-session test-dir test-key)]
          (should= ["session" "message" "message" "compaction" "message"] (mapv :type transcript))
           (should= ["compaction" "message"] (mapv :type active))
           (should (integer? (:effective-history-offset session)))))))

  ;; endregion ^^^^^ splice-compaction! ^^^^^

  ;; region ----- drop-orphan-toolcalls -----

  (describe "drop-orphan-toolcalls"

    (it "returns the original transcript when every tool call has a result"
      (let [transcript [{:type "session" :id "session"}
                        {:type "message"
                         :id "tool-call"
                         :parentId "session"
                         :message {:role "assistant"
                                   :content [{:type "toolCall" :id "tc-1" :name "search" :arguments {}}]}}
                        {:type "message"
                         :id "tool-result"
                         :parentId "tool-call"
                         :message {:role "toolResult" :toolCallId "tc-1" :content "ok"}}]]
        (should= transcript (c/drop-orphan-toolcalls transcript))))

    (it "removes orphan tool call messages and reparents their children"
      (let [transcript [{:type "session" :id "session"}
                        {:type "message"
                         :id "orphan-call"
                         :parentId "session"
                         :message {:role "assistant"
                                   :content [{:type "toolCall" :id "tc-orphan" :name "search" :arguments {}}]}}
                        {:type "message"
                         :id "followup"
                         :parentId "orphan-call"
                         :message {:role "assistant" :content "continuing"}}
                        {:type "message"
                         :id "kept-call"
                         :parentId "followup"
                         :message {:role "assistant"
                                   :content [{:type "toolCall" :id "tc-kept" :name "fetch" :arguments {}}]}}
                        {:type "message"
                         :id "kept-result"
                         :parentId "kept-call"
                         :message {:role "toolResult" :toolCallId "tc-kept" :content "ok"}}]
            result     (c/drop-orphan-toolcalls transcript)]
        (should= ["session" "followup" "kept-call" "kept-result"] (mapv :id result))
        (should= "session" (:parentId (nth result 1)))
        (should= "followup" (:parentId (nth result 2))))))

  ;; endregion ^^^^^ drop-orphan-toolcalls ^^^^^

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
      (sut/append-compaction! test-dir test-key
                              {:summary "All summarized" :firstKeptEntryId nil :tokensBefore 50})
      (sut/append-message! test-dir test-key {:role "user" :content "New question"})
      (sut/truncate-after-compaction! test-dir test-key)
      (let [result (sut/get-transcript test-dir test-key)]
        (should= 3 (count result))
        (should= "session" (:type (nth result 0)))
        (should= "compaction" (:type (nth result 1)))
        (should= "message" (:type (nth result 2)))))

    (it "removes message entries before firstKeptEntryId"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "First"})
      (sut/append-message! test-dir test-key {:role "assistant" :content "Second"})
      (sut/append-message! test-dir test-key {:role "user" :content "Third"})
      (let [transcript   (sut/get-transcript test-dir test-key)
            third-msg-id (:id (last transcript))]
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
      (let [transcript  (sut/get-transcript test-dir test-key)
            second-id   (:id (last transcript))
            session-id  (:id (first transcript))]
        (sut/append-compaction! test-dir test-key
                                {:summary "Summary" :firstKeptEntryId second-id :tokensBefore 50})
        (sut/truncate-after-compaction! test-dir test-key)
        (let [result   (sut/get-transcript test-dir test-key)
              kept-msg (nth result 1)]
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

  ;; region ----- delete-session! -----

  (describe "delete-session!"

    (it "removes the session from the index and deletes the transcript"
      (sut/create-session! test-dir test-key)
      (sut/delete-session! test-dir test-key)
      (should= [] (sut/list-sessions test-dir "main"))
      (should= nil (sut/get-session test-dir test-key)))

    (it "leaves other sessions intact"
      (sut/create-session! test-dir test-key)
      (sut/create-session! test-dir "other")
      (sut/delete-session! test-dir test-key)
      (should= 1 (count (sut/list-sessions test-dir "main")))
      (should-not-be-nil (sut/get-session test-dir "other"))))

  ;; endregion ^^^^^ delete-session! ^^^^^

  ;; region ----- store/create integration -----

  (describe "store/create with :jsonl-edn-index"

    (it "returns a working SessionStore from store/create"
      (let [index-store (store/create test-dir :jsonl-edn-index)]
        (should-not-be-nil index-store)
        (let [session (store/open-session! index-store "test" {})]
          (should= "test" (:id session))
          (should= 1 (count (store/list-sessions index-store)))))))

  ;; endregion ^^^^^ store/create integration ^^^^^
  )
