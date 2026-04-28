(ns isaac.context.manager-spec
  (:require
    [clojure.java.io :as io]
    [isaac.context.manager :as sut]
    [isaac.prompt.builder :as prompt]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))



(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(def test-root "/test/context-manager")

(describe "Context Manager"

  ;; region ----- should-compact? -----

  (describe "should-compact?"

    (it "returns true when total-tokens reaches the default threshold"
      (should (sut/should-compact? {:total-tokens 9000} 10000)))

    (it "returns true when total-tokens equals exactly 80% threshold"
      (should (sut/should-compact? {:total-tokens 800} 1000)))

    (it "returns false when total-tokens is below the configured threshold"
      (should-not (sut/should-compact? {:total-tokens 799} 1000)))

    (it "returns true when total-tokens exceeds context-window"
      (should (sut/should-compact? {:total-tokens 15000} 10000)))

    (it "defaults to 0 when total-tokens is missing"
      (should-not (sut/should-compact? {} 10000)))

    (it "works with small context windows"
      (should (sut/should-compact? {:total-tokens 80} 100))
      (should-not (sut/should-compact? {:total-tokens 79} 100))))

  ;; endregion ^^^^^ should-compact? ^^^^^

  ;; region ----- compact! -----

  (describe "compact!"

    (before-all (clean-dir! test-root))
    (after (clean-dir! test-root))
    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

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
        (should= #{{:name "memory_get"} {:name "memory_search"} {:name "memory_write"}}
                 (set (map #(select-keys % [:name]) (:tools @chat-called))))
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

    (it "restricts the compaction tool surface to memory tools even if others are registered"
      (let [key-str   "isaac:main:cli:chat:memory-only"
            _session  (storage/create-session! test-root key-str)
            _msg      (storage/append-message! test-root key-str {:role "user" :content "hello"})
            captured  (atom nil)
            mock-chat (fn [request _tool-fn _opts]
                        (reset! captured request)
                        {:message {:content "Summary"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 10000
                       :chat-fn        mock-chat})
        (should= ["memory_get" "memory_search" "memory_write"]
                 (sort (map :name (:tools @captured))))))

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
             _config     (storage/update-session! test-root key-str {:compaction {:strategy :slinky :threshold 160 :tail 80}})
             message-1   {:role "user" :content "First question about the project status" :tokens 40}
             message-2   {:role "assistant" :content "The project status is healthy and on track" :tokens 40}
             message-3   {:role "user" :content "Second question about the upcoming release" :tokens 40}
             message-4   {:role "assistant" :content "The release is scheduled for the end of month" :tokens 50}
             _msg1       (storage/append-message! test-root key-str message-1)
             _msg2       (storage/append-message! test-root key-str message-2)
             kept-msg    (storage/append-message! test-root key-str message-3)
             _msg4       (storage/append-message! test-root key-str message-4)
             captured    (atom nil)
             mock-chat   (fn [request _opts]
                           (reset! captured request)
                           {:message {:content "Summary of first exchange"}})
             result      (sut/compact! test-root key-str
                                       {:model          "test-model"
                                        :soul           "You are helpful."
                                        :context-window 200
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

    (it "reduces session total-tokens after compaction"
      (let [key-str   "isaac:main:cli:chat:reset123"
            _session  (storage/create-session! test-root key-str)
            _msg1     (storage/append-message! test-root key-str {:role "user" :content "Please summarize our work"})
            _msg2     (storage/append-message! test-root key-str {:role "assistant" :content "We discussed logging and tools"})
            _tokens   (storage/update-session! test-root key-str {:total-tokens 95})
            mock-chat (fn [_request _opts]
                        {:message {:content "Summary of prior chat"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 100
                       :chat-fn        mock-chat})
        (let [entry (first (storage/list-sessions test-root "main"))]
          (should (< (:total-tokens entry) 90)))))

    (it "resets input-tokens and output-tokens after compaction so total-tokens does not rebound"
      (let [key-str   "isaac:main:cli:chat:rebound123"
            _session  (storage/create-session! test-root key-str)
            _msg1     (storage/append-message! test-root key-str {:role "user" :content "Summarize"})
            _msg2     (storage/append-message! test-root key-str {:role "assistant" :content "Sure"})
            _tokens   (storage/update-tokens! test-root key-str {:input-tokens 120 :output-tokens 30})
            mock-chat (fn [_request _opts]
                        {:message {:content "Summary"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :soul           "You are helpful."
                       :context-window 200
                       :chat-fn        mock-chat})
        (let [entry (storage/get-session test-root key-str)]
          (should= (:total-tokens entry) (+ (:input-tokens entry) (:output-tokens entry)))))))

  ;; endregion ^^^^^ compact! ^^^^^

  )
