(ns isaac.context.manager-spec
  (:require
    [clojure.java.io :as io]
    [isaac.context.manager :as sut]
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

  ;; region ----- truncate-tool-result -----

  (describe "truncate-tool-result"

    (it "returns content unchanged when within limit"
      (let [content "short content"]
        (should= content (sut/truncate-tool-result content 10000))))

    (it "truncates content exceeding max-chars with head-and-tail strategy"
      (let [;; context-window=100 -> max-chars = int(0.3 * 100 * 4) = 120
            ;; We need content longer than 120 chars
            content (apply str (repeat 200 "x"))
            result  (sut/truncate-tool-result content 100)]
        (should-contain "characters truncated" result)
        (should (< (count result) (count content)))))

    (it "preserves head and tail of the content"
      (let [;; context-window=50 -> max-chars = int(0.3 * 50 * 4) = 60, half = 30
            head    (apply str (repeat 30 "H"))
            middle  (apply str (repeat 100 "M"))
            tail    (apply str (repeat 30 "T"))
            content (str head middle tail)
            result  (sut/truncate-tool-result content 50)]
        (should-contain "HHHHH" result)
        (should-contain "TTTTT" result)))

    (it "includes truncation count in the marker"
      (let [;; context-window=50 -> max-chars=60, content=160 chars -> 100 truncated
            content (apply str (repeat 160 "x"))
            result  (sut/truncate-tool-result content 50)]
        (should-contain "100 characters truncated" result)))

    (it "returns content at exactly the limit unchanged"
      (let [;; context-window=50 -> max-chars=60
            content (apply str (repeat 60 "x"))]
        (should= content (sut/truncate-tool-result content 50)))))

  ;; endregion ^^^^^ truncate-tool-result ^^^^^

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
        (should (pos? (:tokensBefore result))))))

  ;; endregion ^^^^^ compact! ^^^^^

  )
