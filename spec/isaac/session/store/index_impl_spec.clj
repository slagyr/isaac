(ns isaac.session.store.index-impl-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.index-impl :as sut]
    [speclj.core :refer :all]))

(def test-dir "/test/index-storage")
(def test-key "user1")

(defn- index-path []
  (str test-dir "/sessions/index.edn"))

(defn- read-index []
  (edn/read-string (fs/slurp (index-path))))

(describe "Index Session Storage"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example] (binding [fs/*fs* (fs/mem-fs)] (example)))

  ;; region ----- create-session! -----

  (describe "create-session!"

    (it "creates a new session and writes to the combined index"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= "user1" (:key entry))
        (should (string? (:sessionId entry)))
        (should (string? (:session-file entry)))
        (should= 0 (:compaction-count entry))
        (should= 0 (:input-tokens entry))
        (should= 0 (:output-tokens entry))
        (should= 0 (:total-tokens entry))
        (should (fs/exists? (index-path)))))

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
            edn-files (->> (or (fs/children dir) [])
                            (filter #(str/ends-with? % ".edn")))]
        (should= ["index.edn"] edn-files)))

    (it "writes session metadata with kebab-case schema keys"
      (sut/create-session! test-dir test-key {:chatType "direct"})
      (let [entry (get (read-index) "user1")]
        (should= "direct" (:chat-type entry))
        (should-not (contains? entry :chatType))))

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
            _      (fs/delete (str test-dir "/sessions/" (:session-file first)))
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
        (fs/mkdirs sessions-dir)
        (fs/spit (str sessions-dir "/chat-1.edn")
                 (binding [*print-namespace-maps* false]
                   (with-out-str (clojure.pprint/pprint sidecar-content))))
        (fs/spit (str sessions-dir "/chat-1.jsonl")
                 (str (json/generate-string {:type "session" :id "abc12345"
                                              :timestamp "2026-05-10T10:00:00"
                                              :version 3 :cwd test-dir}) "\n"))
        (let [sessions (sut/list-sessions test-dir "main")]
          (should= 1 (count sessions))
          (should= "chat-1" (:id (first sessions)))
          (should (fs/exists? (index-path)))))))

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
      (sut/create-session! test-dir test-key)
      (let [before (:updated-at (sut/get-session test-dir test-key))]
        (Thread/sleep 2)
        (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
        (let [after (:updated-at (sut/get-session test-dir test-key))]
          (should-not= before after)))))

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
