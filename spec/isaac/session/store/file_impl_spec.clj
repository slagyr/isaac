(ns isaac.session.store.file-impl-spec
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.fs :as fs]
    [isaac.session.store.file-impl :as sut]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def test-dir "/test/storage")
(def test-key "user1")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- sidecar-path [id]
  (str test-dir "/sessions/" id ".edn"))

(defn- read-sidecar [id]
  (edn/read-string (fs/slurp (sidecar-path id))))

(describe "Session Storage"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example] (binding [fs/*fs* (fs/mem-fs)] (example)))

  (describe "normalize-index-store"

    (it "normalizes map stores with keyword keys and non-map entries"
      (let [result (#'sut/normalize-index-store {:alpha {:session-file "a.jsonl"}
                                                 :beta  "not-a-map"})]
        (should= #{{:id "alpha" :key "alpha" :session-file "a.jsonl"}
                   {:id "beta" :key "beta"}}
                 (set (map #(select-keys % [:id :key :session-file]) (vals result))))))

    (it "normalizes sequential stores and skips blank ids"
      (let [result (#'sut/normalize-index-store [{:key "alpha" :session-file "a.jsonl"}
                                                 {:id "beta" :session-file "b.jsonl"}
                                                 {:id ""}
                                                 "not-a-map"])]
        (should= #{{:id "alpha" :key "alpha" :session-file "a.jsonl"}
                   {:id "beta" :key "beta" :session-file "b.jsonl"}}
                 (set (map #(select-keys % [:id :key :session-file]) (vals result)))))))

  ;; region ----- create-session! -----

  (describe "create-session!"

    (it "creates a new session with sidecar and transcript"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= "user1" (:key entry))
        (should (string? (:sessionId entry)))
        (should (string? (:session-file entry)))
        (should-be-nil (:channel entry))
        (should-be-nil (:chat-type entry))
        (should-not (contains? entry :chatType))
        (should= 0 (:compaction-count entry))
        (should= 0 (:input-tokens entry))
        (should= 0 (:output-tokens entry))
        (should= 0 (:total-tokens entry))))

    (it "writes session metadata with kebab-case schema keys"
      (sut/create-session! test-dir test-key {:chatType "direct"})
      (let [entry (read-sidecar test-key)]
        (should= "direct" (:chat-type entry))
        (should (contains? entry :created-at))
        (should-not (contains? entry :chatType))
        (should-not (contains? entry :createdAt))))

    (it "does not include an agent field on newly created sessions"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= "main" (:crew entry))
        (should-not (contains? entry :agent))))

    (it "stores cwd in the sidecar entry"
      (let [entry (sut/create-session! test-dir test-key)]
        (should (string? (:cwd entry)))
        (should (not (clojure.string/blank? (:cwd entry))))))

    (it "defaults origin to cli when none is provided"
      (let [entry (sut/create-session! test-dir test-key)]
        (should= {:kind :cli} (:origin entry))))

    (it "stores an explicit origin in the sidecar entry"
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

    (it "rejects a different session name that slug-collides with an existing session"
      (sut/create-session! test-dir "friday-debug")
      (let [error (try
                    (sut/create-session! test-dir "Friday Debug")
                    nil
                    (catch clojure.lang.ExceptionInfo e
                      e))]
        (should-not-be-nil error)
        (should= "session already exists: friday-debug" (ex-message error))))

    (it "creates a fresh session when the sidecar exists but its transcript is missing"
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
      (sut/create-session! test-dir "user2")
      (should= 2 (count (sut/list-sessions test-dir "main"))))

    (it "writes sidecar files keyed by session id"
      (let [entry      (sut/create-session! test-dir "Friday Debug!")
            entry-map   (read-sidecar "friday-debug")]
        (should= "Friday Debug!" (:name entry-map))
        (should= (:session-file entry) (:session-file entry-map))))

    (it "migrates a legacy index entry into a sidecar on read"
      (let [session-file "legacy.jsonl"
            index-path    (str test-dir "/sessions/index.edn")]
        (fs/mkdirs (str test-dir "/sessions"))
        (fs/spit index-path (pr-str {"legacy" {:id           "legacy"
                                                :key          "legacy"
                                                :name         "Legacy"
                                                :session-file session-file
                                                :createdAt    "2026-05-08T10:00:00"
                                                :updated-at   "2026-05-08T10:00:00"
                                                :chatType     "direct"}}))
        (fs/spit (str test-dir "/sessions/" session-file)
                 (str (json/generate-string {:type "session"
                                             :id "header1"
                                             :timestamp "2026-05-08T10:00:00"
                                             :version 3
                                             :cwd test-dir}) "\n"))
        (let [entry (sut/get-session test-dir "legacy")]
          (should= "2026-05-08T10:00:00" (:created-at entry))
          (should= "direct" (:chat-type entry))
          (should (fs/exists? (sidecar-path "legacy")))
          (should-not (contains? entry :createdAt))
          (should-not (contains? entry :chatType))))))

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

    (it "updates last-channel and last-to on routing messages"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "Hi" :channel "telegram" :to "bob"})
      (let [listing (sut/list-sessions test-dir "main")
            entry   (first listing)]
        (should= "telegram" (:last-channel entry))
        (should= "bob" (:last-to entry))))

    (it "does not add an agent field when assistant messages resolve a crew"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "assistant" :content "Hello"})
      (let [entry      (first (sut/list-sessions test-dir "main"))
            transcript (sut/get-transcript test-dir test-key)
            message    (get-in (last transcript) [:message])]
        (should= "main" (:crew entry))
        (should-not (contains? entry :agent))
        (should= "main" (:crew message))
        (should-not (contains? message :agent)))))

  ;; endregion ^^^^^ append-message! ^^^^^

  ;; region ----- get-transcript -----

  (describe "get-transcript"

    (it "does not log orphan tool call diagnostics"
      (sut/create-session! test-dir test-key)
      (sut/append-message! test-dir test-key {:role "user" :content "What's in fridge.txt?"})
      (sut/append-message! test-dir test-key {:role    "assistant"
                                              :content [{:type      "toolCall"
                                                         :id        "call_old"
                                                         :name      "read"
                                                         :arguments {:filePath "fridge.txt"}}]})
      (log/capture-logs
        (let [transcript (sut/get-transcript test-dir test-key)
              events     (map :event @log/captured-logs)]
          (should= 3 (count transcript))
          (should-not-contain :transcript/orphan-toolcalls-detected events)))))

  ;; endregion ^^^^^ get-transcript ^^^^^

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

    (it "updates arbitrary fields on the sidecar entry"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:input-tokens 42})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 42 (:input-tokens entry))))

    (it "normalizes updated-at to ISO timestamp"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:updated-at 1000})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should (string? (:updated-at entry)))
        (should (re-find #"^\d{4}-\d{2}-\d{2}T" (:updated-at entry)))))

    (it "conforms legacy-shaped updates before writing the sidecar"
      (sut/create-session! test-dir test-key)
      (sut/update-session! test-dir test-key {:createdAt "2026-05-08T10:00:00" :chatType "direct"})
      (let [entry (read-sidecar test-key)]
        (should= "2026-05-08T10:00:00" (:created-at entry))
        (should= "direct" (:chat-type entry))
        (should-not (contains? entry :createdAt))
        (should-not (contains? entry :chatType)))))

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
          (should= 1 (:compaction-count entry))))))

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

    (it "creates a .bak.jsonl backup before rewriting the transcript"
      (sut/create-session! test-dir test-key)
      (let [session-id (:id (first (sut/get-transcript test-dir test-key)))
            first-msg  (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
            second-msg (sut/append-message! test-dir test-key {:role "assistant" :content "Hi"})]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Compacted"
                                 :firstKeptEntryId  nil
                                 :tokensBefore      10
                                 :compactedEntryIds [(:id first-msg) (:id second-msg)]})
        (let [sessions-dir (str test-dir "/sessions")
              session-file (:session-file (sut/get-session test-dir test-key))
              session-base (subs session-file 0 (- (count session-file) (count ".jsonl")))
              backups      (->> (fs/children sessions-dir)
                                (filter #(and (str/starts-with? % session-base)
                                              (str/ends-with? % ".bak.jsonl"))))]
          (should= 1 (count backups)))))

    (it "backup file contains the pre-splice transcript"
      (sut/create-session! test-dir test-key)
      (let [first-msg  (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
            second-msg (sut/append-message! test-dir test-key {:role "assistant" :content "Hi"})
            pre-splice (sut/get-transcript test-dir test-key)]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Compacted"
                                 :firstKeptEntryId  nil
                                 :tokensBefore      10
                                 :compactedEntryIds [(:id first-msg) (:id second-msg)]})
        (let [sessions-dir (str test-dir "/sessions")
              session-file (:session-file (sut/get-session test-dir test-key))
              session-base (subs session-file 0 (- (count session-file) (count ".jsonl")))
              bak-name     (->> (fs/children sessions-dir)
                                (filter #(and (str/starts-with? % session-base)
                                              (str/ends-with? % ".bak.jsonl")))
                                first)
              bak-content  (->> (str/split-lines (fs/slurp (str sessions-dir "/" bak-name)))
                                 (remove str/blank?)
                                 (mapv #(json/parse-string % true)))]
          (should= (count pre-splice) (count bak-content))
          (should= (mapv :id pre-splice) (mapv :id bak-content)))))

    (it "does not log splice diagnostics"
      (sut/create-session! test-dir test-key)
      (let [first-msg  (sut/append-message! test-dir test-key {:role "user" :content "Hello"})
            second-msg (sut/append-message! test-dir test-key {:role "assistant" :content "Hi"})]
        (log/capture-logs
          (sut/splice-compaction! test-dir test-key
                                  {:summary           "Compacted"
                                   :firstKeptEntryId  nil
                                   :tokensBefore      10
                                   :compactedEntryIds [(:id first-msg) (:id second-msg)]})
          (let [events (map :event @log/captured-logs)]
            (should-not-contain :transcript/splice-start events)
            (should-not-contain :transcript/splice-written events)))))

    (it "drops orphan tool calls after compaction splice"
      (sut/create-session! test-dir test-key)
      (let [_msg1      (sut/append-message! test-dir test-key {:role "user" :content "What's in fridge.txt?"})
            _tool-call (sut/append-message! test-dir test-key {:role    "assistant"
                                                               :content [{:type      "toolCall"
                                                                          :id        "call_old"
                                                                          :name      "read"
                                                                          :arguments {:filePath "fridge.txt"}}]})
            tool-result (sut/append-message! test-dir test-key {:role "toolResult" :id "call_old" :content "one sad lemon"})
            kept-msg   (sut/append-message! test-dir test-key {:role "assistant" :content "The fridge has a lemon."})]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Summary"
                                 :firstKeptEntryId  (:id kept-msg)
                                 :tokensBefore      20
                                 :compactedEntryIds [(:id tool-result)]})
        (let [transcript (sut/get-transcript test-dir test-key)
              rendered   (pr-str transcript)]
          (should-not-contain "call_old" rendered)
          (should-contain "The fridge has a lemon." rendered)))))

    (it "preserves paired tool calls after compaction splice when results use toolCallId"
      (sut/create-session! test-dir test-key)
      (let [old-msg     (sut/append-message! test-dir test-key {:role "user" :content "Earlier question"})
            tool-call   (sut/append-message! test-dir test-key {:role    "assistant"
                                                                :content [{:type      "toolCall"
                                                                           :id        "call_old"
                                                                           :name      "read"
                                                                           :arguments {:filePath "fridge.txt"}}]})
            _tool-result (sut/append-message! test-dir test-key {:role "toolResult" :toolCallId "call_old" :content "one sad lemon"})
            kept-msg    (sut/append-message! test-dir test-key {:role "assistant" :content "The fridge has a lemon."})]
        (sut/splice-compaction! test-dir test-key
                                {:summary           "Summary"
                                 :firstKeptEntryId  (:id tool-call)
                                 :tokensBefore      20
                                 :compactedEntryIds [(:id old-msg)]})
        (let [transcript    (sut/get-transcript test-dir test-key)
              tool-call-ids (->> transcript
                                 (filter #(= "message" (:type %)))
                                 (mapcat (fn [entry]
                                           (->> (get-in entry [:message :content])
                                                (filter #(= "toolCall" (:type %)))
                                                (map :id))))
                                 set)
              rendered      (pr-str transcript)]
          (should-contain "call_old" tool-call-ids)
          (should-contain "one sad lemon" rendered)
          (should-contain "The fridge has a lemon." rendered))))

    (it "keeps only the 8 most recent backups after pruning"
      (sut/create-session! test-dir test-key)
      (let [session-file (:session-file (sut/get-session test-dir test-key))
            session-base (subs session-file 0 (- (count session-file) (count ".jsonl")))
            sessions-dir (str test-dir "/sessions")]
        (doseq [i (range 9)]
          (let [msg (sut/append-message! test-dir test-key {:role "user" :content (str "msg-" i)})]
            (sut/splice-compaction! test-dir test-key
                                    {:summary           (str "Summary " i)
                                     :firstKeptEntryId  nil
                                     :tokensBefore      10
                                     :compactedEntryIds [(:id msg)]})))
        (let [backups (->> (fs/children sessions-dir)
                           (filter #(and (str/starts-with? % session-base)
                                         (str/ends-with? % ".bak.jsonl"))))]
          (should= 8 (count backups)))))

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
      (sut/update-tokens! test-dir test-key {:input-tokens 10 :output-tokens 5})
      (sut/update-tokens! test-dir test-key {:input-tokens 20 :output-tokens 15})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 30 (:input-tokens entry))
        (should= 20 (:output-tokens entry))
        (should= 50 (:total-tokens entry))
        (should= 20 (:last-input-tokens entry))))

    (it "replaces last-input-tokens instead of accumulating it"
      (sut/create-session! test-dir test-key)
      (sut/update-tokens! test-dir test-key {:input-tokens 10 :output-tokens 5})
      (sut/update-tokens! test-dir test-key {:input-tokens 42 :output-tokens 1})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 42 (:last-input-tokens entry))
        (should= 58 (:total-tokens entry))))

    (it "tracks cache tokens when provided"
      (sut/create-session! test-dir test-key)
      (sut/update-tokens! test-dir test-key {:input-tokens 10 :output-tokens 5 :cache-read 3 :cache-write 2})
      (let [entry (first (sut/list-sessions test-dir "main"))]
        (should= 3 (:cache-read entry))
        (should= 2 (:cache-write entry)))))

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

  )
