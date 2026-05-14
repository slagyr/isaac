(ns isaac.session.store.memory-spec
  (:require
    [isaac.session.store :as store]
    [isaac.session.store.memory :as sut]
    [speclj.core :refer :all]))

(describe "MemorySessionStore"

  (describe "open-session!"

    (it "creates a session with transcript metadata"
      (let [s     (sut/create-store)
            entry (store/open-session! s "friday-debug" {:crew "main"})]
        (should= "friday-debug" (:id entry))
        (should= "friday-debug.jsonl" (:session-file entry))
        (should= {:kind :cli} (:origin entry))
        (should= 0 (:compaction-count entry))
        (should= 1 (count (store/get-transcript s "friday-debug")))))

    (it "reuses an existing session"
      (let [s     (sut/create-store)
            first (store/open-session! s "friday-debug" {:crew "main"})
            again (store/open-session! s "friday-debug" {:crew "main"})]
        (should= (:sessionId first) (:sessionId again)))))

  (describe "append-message!"

    (it "appends transcript messages with parent links"
      (let [s (sut/create-store)]
        (store/open-session! s "chat" {:crew "main"})
        (store/append-message! s "chat" {:role "user" :content "Hello"})
        (store/append-message! s "chat" {:role "assistant" :content "Hi"})
        (let [transcript (store/get-transcript s "chat")
              header     (nth transcript 0)
              user-msg   (nth transcript 1)
              asst-msg   (nth transcript 2)]
          (should= (:id header) (:parentId user-msg))
          (should= (:id user-msg) (:parentId asst-msg))
          (should= [{:type "text" :text "Hello"}] (get-in user-msg [:message :content])))))

    (it "updates last-channel and last-to metadata"
      (let [s (sut/create-store)]
        (store/open-session! s "chat" {:crew "main"})
        (store/append-message! s "chat" {:role "user" :content "Hello" :channel "discord" :to "bob"})
        (let [entry (store/get-session s "chat")]
          (should= "discord" (:last-channel entry))
          (should= "bob" (:last-to entry))))))

  (describe "update-session!"

    (it "merges compaction state"
      (let [s (sut/create-store)]
        (store/open-session! s "chat" {:crew "main"})
        (store/update-session! s "chat" {:compaction {:strategy :slinky :threshold 80}})
        (store/update-session! s "chat" {:compaction {:tail 40}})
        (should= {:strategy :slinky :threshold 80 :tail 40}
                 (:compaction (store/get-session s "chat"))))))

  (describe "append-compaction! and truncate-after-compaction!"

    (it "appends compaction entries and truncates old messages"
      (let [s (sut/create-store)]
        (store/open-session! s "chat" {:crew "main"})
        (store/append-message! s "chat" {:role "user" :content "First"})
        (let [kept (store/append-message! s "chat" {:role "assistant" :content "Second"})]
          (store/append-compaction! s "chat" {:summary "Summary" :firstKeptEntryId (:id kept) :tokensBefore 10})
          (store/truncate-after-compaction! s "chat")
          (let [transcript (store/get-transcript s "chat")]
            (should= ["session" "message" "compaction"] (mapv :type transcript))
            (should= "Second" (get-in (nth transcript 1) [:message :content])))))))

  (describe "splice-compaction!"

    (it "splices compaction entries into the transcript"
      (let [s (sut/create-store)]
        (store/open-session! s "chat" {:crew "main"})
        (let [m1 (store/append-message! s "chat" {:role "user" :content "First"})
              m2 (store/append-message! s "chat" {:role "assistant" :content "Second"})
              m3 (store/append-message! s "chat" {:role "user" :content "Third"})]
           (store/splice-compaction! s "chat" {:summary "Summary"
                                                :firstKeptEntryId (:id m3)
                                                :tokensBefore 20
                                                :compactedEntryIds [(:id m1) (:id m2)]})
          (let [transcript (store/get-transcript s "chat")]
            (should= ["session" "compaction" "message"] (mapv :type transcript))
            (should= (:id m3) (:id (nth transcript 2))))))))

  )
