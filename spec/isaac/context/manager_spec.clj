(ns isaac.context.manager-spec
  (:require
    [clojure.java.io :as io]
    [isaac.context.manager :as sut]
    [isaac.logger :as log]
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
        (should= #{"memory_get" "memory_search" "memory_write"}
                 (set (map #(or (:name %) (get-in % [:function :name])) (:tools @chat-called))))
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
                 (sort (map #(or (:name %) (get-in % [:function :name])) (:tools @captured))))))

    (it "formats compaction tools for codex responses requests"
      (let [key-str   "isaac:main:cli:chat:codex-tools"
            _session  (storage/create-session! test-root key-str)
            _msg      (storage/append-message! test-root key-str {:role "user" :content "hello"})
            captured  (atom nil)
            mock-chat (fn [request _tool-fn _opts]
                        (reset! captured request)
                        {:message {:content "Summary"}})]
        (sut/compact! test-root key-str
                      {:model          "test-model"
                       :provider       "openai-codex"
                       :soul           "You are helpful."
                       :context-window 10000
                       :chat-fn        mock-chat})
        (should= #{{:type "function" :name "memory_get"}
                   {:type "function" :name "memory_search"}
                   {:type "function" :name "memory_write"}}
                 (set (map #(select-keys % [:type :name]) (:tools @captured))))))

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

    (it "logs compaction calculations and chunk planning details"
      (let [key-str    "isaac:main:cli:chat:calc123"
            _session   (storage/create-session! test-root key-str)
            _msg1      (storage/append-message! test-root key-str {:role "user" :content "block A (oldest)" :tokens 70})
            _msg2      (storage/append-message! test-root key-str {:role "assistant" :content "reply A" :tokens 70})
            _msg3      (storage/append-message! test-root key-str {:role "user" :content "block B" :tokens 70})
            _msg4      (storage/append-message! test-root key-str {:role "assistant" :content "reply B" :tokens 70})
            mock-chat  (fn [request _opts]
                         (let [body (-> request :messages second :content)]
                           (cond
                             (re-find #"block A" body) {:message {:content "A1"}}
                             (re-find #"reply A" body) {:message {:content "A2"}}
                             (re-find #"block B" body) {:message {:content "B1"}}
                             (re-find #"reply B" body) {:message {:content "B2"}}
                             :else                     {:message {:content "AB"}})))]
        (log/capture-logs
          (with-redefs [prompt/estimate-tokens (fn [prompt]
                                                 (let [body (-> prompt :messages second :content)]
                                                   (cond
                                                     (and (re-find #"block A" body) (re-find #"reply A" body)) 320
                                                     (and (re-find #"block B" body) (re-find #"reply B" body)) 320
                                                     (and (re-find #"block A" body) (re-find #"block B" body)) 600
                                                     (re-find #"A1|A2|B1|B2" body) 200
                                                     :else 150)))]
            (sut/compact! test-root key-str
                          {:model          "test-model"
                           :soul           "You are helpful."
                           :context-window 300
                           :chat-fn        mock-chat})
            (let [analysis (first (filter #(= :session/compaction-analysis (:event %)) @log/captured-logs))
                  plan     (first (filter #(= :session/compaction-chunk-plan (:event %)) @log/captured-logs))]
              (should-not-be-nil analysis)
              (should-not-be-nil plan)
              (should= key-str (:session analysis))
              (should= 300 (:context-window analysis))
              (should= 4 (:compactable-head-count analysis))
              (should= 320 (:summary-prompt-tokens analysis))
              (should= true (:needs-chunking analysis))
              (should= 3 (:chunk-count plan))
              (should= [150 150 150] (:chunk-request-tokens plan))))))

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
          (should= (:total-tokens entry) (+ (:input-tokens entry) (:output-tokens entry))))))

    (it "chunks compaction when the one-shot summary request exceeds the context window"
      (let [key-str    "isaac:main:cli:chat:chunk123"
             _session   (storage/create-session! test-root key-str)
             _msg1      (storage/append-message! test-root key-str {:role "user" :content "block A (oldest)" :tokens 60})
             _msg2      (storage/append-message! test-root key-str {:role "assistant" :content "reply A" :tokens 60})
             _msg3      (storage/append-message! test-root key-str {:role "user" :content "block B" :tokens 60})
             _msg4      (storage/append-message! test-root key-str {:role "assistant" :content "reply B" :tokens 60})
             _msg5      (storage/append-message! test-root key-str {:role "user" :content "latest question" :tokens 61})
             calls      (atom [])
             mock-chat  (fn [request _opts]
                          (swap! calls conj request)
                          (if (> (prompt/estimate-tokens request) 300)
                            {:error :llm-error :message "context length exceeded"}
                            (let [body (-> request :messages second :content)]
                              (cond
                                (and (re-find #"block A" body) (re-find #"reply A" body))
                                {:message {:content "A"}}

                                (and (re-find #"block B" body) (re-find #"reply B" body))
                                {:message {:content "B"}}

                                (re-find #"latest question" body)
                                {:message {:content "Q"}}

                                :else
                                {:message {:content "ABQ"}}))))]
        (log/capture-logs
          (let [result (sut/compact! test-root key-str
                                     {:model          "test-model"
                                      :soul           "You are helpful."
                                      :context-window 300
                                      :chat-fn        mock-chat})]
            (should= "ABQ" (:summary result))
            (should= 3 (count @calls))
            (let [entry (first (filter #(= :session/compaction-chunked (:event %)) @log/captured-logs))]
              (should-not-be-nil entry)
              (should= key-str (:session entry)))))))

    (it "chunks compaction even when only four compacted entries overflow one shot"
      (let [key-str    "isaac:main:cli:chat:chunk4"
             _session   (storage/create-session! test-root key-str)
             _msg1      (storage/append-message! test-root key-str {:role "user" :content "block A (oldest)" :tokens 70})
             _msg2      (storage/append-message! test-root key-str {:role "assistant" :content "reply A" :tokens 70})
             _msg3      (storage/append-message! test-root key-str {:role "user" :content "block B" :tokens 70})
             _msg4      (storage/append-message! test-root key-str {:role "assistant" :content "reply B" :tokens 70})
             calls      (atom [])
             mock-chat  (fn [request _opts]
                          (swap! calls conj request)
                          (if (> (prompt/estimate-tokens request) 300)
                            {:error :llm-error :message "context length exceeded"}
                            (let [body (-> request :messages second :content)]
                              (cond
                                (re-find #"block A" body)
                                {:message {:content "A1"}}

                                (re-find #"reply A" body)
                                {:message {:content "A2"}}

                                (re-find #"block B" body)
                                {:message {:content "B1"}}

                                (re-find #"reply B" body)
                                {:message {:content "B2"}}

                                :else
                                {:message {:content "AB"}}))))]
        (log/capture-logs
          (with-redefs [prompt/estimate-tokens (fn [prompt]
                                                 (let [body (-> prompt :messages second :content)]
                                                   (cond
                                                     (and (re-find #"block A" body) (re-find #"reply A" body)) 320
                                                     (and (re-find #"block B" body) (re-find #"reply B" body)) 320
                                                     (and (re-find #"block A" body) (re-find #"block B" body)) 600
                                                     (re-find #"A1|A2|B1|B2" body) 200
                                                     :else 150)))]
            (let [result (sut/compact! test-root key-str
                                       {:model          "test-model"
                                        :soul           "You are helpful."
                                        :context-window 300
                                        :chat-fn        mock-chat})]
              (should= "AB" (:summary result))
              (should= 4 (count @calls))
              (let [entry (first (filter #(= :session/compaction-chunked (:event %)) @log/captured-logs))]
                (should-not-be-nil entry)
                (should= key-str (:session entry))))))))

    (it "chunks compaction for normal transcript entries without explicit token metadata"
      (let [key-str    "isaac:main:cli:chat:livechunk123"
             _session   (storage/create-session! test-root key-str)
             _msg1      (storage/append-message! test-root key-str {:role "user" :content "block A (oldest)"})
            _msg2      (storage/append-message! test-root key-str {:role "assistant" :content "reply A"})
            _msg3      (storage/append-message! test-root key-str {:role "user" :content "block B"})
            _msg4      (storage/append-message! test-root key-str {:role "assistant" :content "reply B"})
            _msg5      (storage/append-message! test-root key-str {:role "user" :content "latest question"})
            calls      (atom [])
            mock-chat  (fn [request _opts]
                         (swap! calls conj request)
                         (let [body (-> request :messages second :content)]
                           (cond
                             (and (re-find #"block A" body) (re-find #"reply A" body) (not (re-find #"block B" body)))
                             {:message {:content "summary of A"}}

                             (and (re-find #"block B" body) (re-find #"reply B" body))
                             {:message {:content "summary of B"}}

                             :else
                             {:message {:content "summary of summaries"}})))]
        (log/capture-logs
          (with-redefs [prompt/estimate-tokens (fn [prompt]
                                                 (let [messages (:messages prompt)
                                                       body     (-> (if (= 1 (count messages))
                                                                      (first messages)
                                                                      (second messages))
                                                                    :content)]
                                                   (if (= 1 (count messages))
                                                     (cond
                                                        (re-find #"block A|reply A|block B|reply B" body) 15
                                                        (re-find #"latest question" body)                10
                                                        :else                                              10)
                                                      (cond
                                                        (and (re-find #"block A" body) (re-find #"block B" body) (re-find #"latest question" body)) 220
                                                        :else 40))))]
            (let [result (sut/compact! test-root key-str
                                       {:model          "test-model"
                                        :soul           "You are helpful."
                                        :context-window 60
                                        :chat-fn        mock-chat})]
              (should= "summary of summaries" (:summary result))
              (should= 3 (count @calls))
              (let [entry (first (filter #(= :session/compaction-chunked (:event %)) @log/captured-logs))]
                (should-not-be-nil entry)
                (should= key-str (:session entry))))))))

  ;; endregion ^^^^^ compact! ^^^^^

  )))
