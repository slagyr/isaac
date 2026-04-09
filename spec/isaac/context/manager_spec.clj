(ns isaac.context.manager-spec
  (:require
    [clojure.java.io :as io]
    [isaac.context.manager :as sut]
    [isaac.prompt.builder :as prompt]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))



(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(def test-root "target/test-context-manager")

(describe "Context Manager"

  ;; region ----- should-compact? -----

  (describe "should-compact?"

    (it "returns true when totalTokens >= 90% of context-window"
      (should (sut/should-compact? {:totalTokens 9000} 10000)))

    (it "returns true when totalTokens equals exactly 90% threshold"
      (should (sut/should-compact? {:totalTokens 900} 1000)))

    (it "returns false when totalTokens is below 90% of context-window"
      (should-not (sut/should-compact? {:totalTokens 8999} 10000)))

    (it "returns true when totalTokens exceeds context-window"
      (should (sut/should-compact? {:totalTokens 15000} 10000)))

    (it "defaults to 0 when totalTokens is missing"
      (should-not (sut/should-compact? {} 10000)))

    (it "works with small context windows"
      (should (sut/should-compact? {:totalTokens 90} 100))
      (should-not (sut/should-compact? {:totalTokens 89} 100))))

  ;; endregion ^^^^^ should-compact? ^^^^^

  ;; region ----- compact! -----

  (describe "compact!"

    (before-all (clean-dir! test-root))
    (after (clean-dir! test-root))

    (it "calls chat-fn with summary prompt and appends compaction"
      (let [key-str  "isaac:main:cli:chat:abc123"
            _session (storage/create-session! test-root key-str)
            _msg1    (storage/append-message! test-root key-str
                       {:role "user" :content "Hello"})
            _msg2    (storage/append-message! test-root key-str
                       {:role "assistant" :content "Hi there!"})
            chat-called (atom nil)
            mock-chat  (fn [request _opts]
                         (reset! chat-called request)
                         {:message {:content "Summary of conversation"}})
            result   (sut/compact! test-root key-str
                       {:model          "test-model"
                        :soul           "You are helpful."
                        :context-window 10000
                        :chat-fn        mock-chat})]
        ;; chat-fn was called
        (should-not-be-nil @chat-called)
        ;; chat-fn received the correct model
        (should= "test-model" (:model @chat-called))
        ;; chat-fn received system + user messages
        (should= 2 (count (:messages @chat-called)))
        (should= "system" (-> @chat-called :messages first :role))
        ;; result is a compaction entry
        (should= "compaction" (:type result))
        (should= "Summary of conversation" (:summary result))))

    (it "returns error when chat-fn returns error"
      (let [key-str  "isaac:main:cli:chat:err123"
            _session (storage/create-session! test-root key-str)
            _msg     (storage/append-message! test-root key-str
                       {:role "user" :content "Hello"})
            mock-chat (fn [_request _opts]
                        {:error "LLM unavailable"})
            result    (sut/compact! test-root key-str
                         {:model          "test-model"
                          :soul           "You are helpful."
                          :context-window 10000
                          :chat-fn        mock-chat})]
        (should= "LLM unavailable" (:error result))))

    (it "supports chat functions that only accept a request"
      (let [key-str   "isaac:main:cli:chat:arity123"
            _session  (storage/create-session! test-root key-str)
            _msg      (storage/append-message! test-root key-str
                        {:role "user" :content "Hello"})
            captured  (atom nil)
            mock-chat (fn [request]
                        (reset! captured request)
                        {:message {:content "Summary"}})
            result    (sut/compact! test-root key-str
                         {:model          "test-model"
                          :soul           "You are helpful."
                          :context-window 10000
                          :chat-fn        mock-chat})]
        (should-not-be-nil @captured)
        (should= "Summary" (:summary result))))

    (it "records tokensBefore in the compaction entry"
      (let [key-str  "isaac:main:cli:chat:tok123"
            _session (storage/create-session! test-root key-str)
            _msg     (storage/append-message! test-root key-str
                       {:role "user" :content "Some message content"})
            mock-chat (fn [_request _opts]
                        {:message {:content "Summary"}})
            result    (sut/compact! test-root key-str
                          {:model          "test-model"
                           :soul           "You are helpful."
                           :context-window 10000
                           :chat-fn        mock-chat})]
        (should (pos? (:tokensBefore result)))))

    (it "compacts only the oldest messages that fit in the context window"
      (let [key-str     "isaac:main:cli:chat:partial123"
            _session    (storage/create-session! test-root key-str)
            message-1   {:role "user" :content "First question about the project status"}
            message-2   {:role "assistant" :content "The project status is healthy and on track"}
            message-3   {:role "user" :content "Second question about the upcoming release"}
            message-4   {:role "assistant" :content "The release is scheduled for the end of month"}
            _msg1       (storage/append-message! test-root key-str message-1)
            _msg2       (storage/append-message! test-root key-str message-2)
            kept-msg    (storage/append-message! test-root key-str message-3)
            _msg4       (storage/append-message! test-root key-str message-4)
            messages    [message-1 message-2 message-3 message-4]
            fit-window  (prompt/estimate-tokens {:messages (subvec messages 0 2)})
            next-window (prompt/estimate-tokens {:messages (subvec messages 0 3)})
            captured    (atom nil)
            mock-chat   (fn [request _opts]
                          (reset! captured request)
                          {:message {:content "Summary of first exchange"}})
            result      (sut/compact! test-root key-str
                                      {:model          "test-model"
                                       :soul           "You are helpful."
                                       :context-window (quot (+ fit-window next-window) 2)
                                       :chat-fn        mock-chat})
            prompt-body (-> @captured :messages second :content)]
        (should-contain "First question about the project status" prompt-body)
        (should-contain "The project status is healthy and on track" prompt-body)
        (should-not-contain "Second question about the upcoming release" prompt-body)
        (should-not-contain "The release is scheduled for the end of month" prompt-body)
        (should= (:id kept-msg) (:firstKeptEntryId result))))

    (it "on a later pass, compacts the current compacted history instead of raw transcript messages"
      (let [key-str      "isaac:main:cli:chat:repeat123"
            _session     (storage/create-session! test-root key-str)
            _msg1        (storage/append-message! test-root key-str {:role "user" :content "Older question"})
            _msg2        (storage/append-message! test-root key-str {:role "assistant" :content "Older answer"})
            kept-msg     (storage/append-message! test-root key-str {:role "user" :content "Recent question"})
            _msg4        (storage/append-message! test-root key-str {:role "assistant" :content "Recent answer"})
            _compact     (storage/append-compaction! test-root key-str
                                                    {:summary          "Summary from first compact"
                                                     :firstKeptEntryId (:id kept-msg)
                                                     :tokensBefore     62})
            captured     (atom nil)
            mock-chat    (fn [request _opts]
                           (reset! captured request)
                           {:message {:content "Summary from second compact"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 10000
                       :chat-fn        mock-chat})
        (let [prompt-body (-> @captured :messages second :content)]
          (should-contain "Summary from first compact" prompt-body)
          (should-contain "Recent question" prompt-body)
          (should-contain "Recent answer" prompt-body)
          (should-not-contain "Older question" prompt-body)
          (should-not-contain "Older answer" prompt-body))))

    (it "reduces session totalTokens after compaction"
      (let [key-str   "isaac:main:cli:chat:reset123"
            _session  (storage/create-session! test-root key-str)
            _msg1     (storage/append-message! test-root key-str {:role "user" :content "Please summarize our work"})
            _msg2     (storage/append-message! test-root key-str {:role "assistant" :content "We discussed logging and tools"})
            _tokens   (storage/update-session! test-root key-str {:totalTokens 95})
            mock-chat (fn [_request _opts]
                        {:message {:content "Summary of prior chat"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 100
                       :chat-fn        mock-chat})
        (let [entry (first (storage/list-sessions test-root "main"))]
          (should (< (:totalTokens entry) 90)))))

    (it "resets inputTokens and outputTokens after compaction so totalTokens does not rebound"
      (let [key-str   "isaac:main:cli:chat:rebound123"
            _session  (storage/create-session! test-root key-str)
            _msg1     (storage/append-message! test-root key-str {:role "user" :content "Summarize"})
            _msg2     (storage/append-message! test-root key-str {:role "assistant" :content "Sure"})
            _tokens   (storage/update-tokens! test-root key-str {:inputTokens 120 :outputTokens 30})
            mock-chat (fn [_request _opts]
                        {:message {:content "Summary"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 200
                       :chat-fn        mock-chat})
        (let [entry (first (filter #(= key-str (:key %)) (storage/list-sessions test-root "main")))]
          (should= (:totalTokens entry) (+ (:inputTokens entry) (:outputTokens entry)))))))

  ;; endregion ^^^^^ compact! ^^^^^

  )
