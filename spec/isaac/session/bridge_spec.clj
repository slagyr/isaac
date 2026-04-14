(ns isaac.session.bridge-spec
  (:require
    [clojure.java.io :as io]
    [speclj.core :refer :all]
    [isaac.session.bridge :as bridge]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

(defn- delete-dir! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))))

(describe "bridge/status-data"
  (with-all state-dir "target/test-state/bridge-spec")

  (before
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "agent:main:cli:direct:testuser"))

  (it "includes crew, model, provider from context"
    (let [ctx    {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
          data   (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= "main"   (:crew data))
      (should= "echo"   (:model data))
      (should= "grover" (:provider data))))

  (it "includes context-window from context"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= 32768 (:context-window data))))

  (it "includes session-key"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= "agent:main:cli:direct:testuser" (:session-key data))))

  (it "includes session-file from storage"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "testuser" ctx)]
      (should-not-be-nil (:session-file data))
      (should (re-matches #"[a-z0-9-]+\.jsonl" (:session-file data)))))

  (it "counts zero turns on a fresh session"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= 0 (:turns data))))

  (it "counts turns from transcript messages"
    (storage/append-message! @state-dir "agent:main:cli:direct:testuser" {:role "user" :content "hello"})
    (storage/append-message! @state-dir "agent:main:cli:direct:testuser" {:role "assistant" :content "hi"})
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= 2 (:turns data))))

  (it "includes compaction count from storage"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= 0 (:compactions data))))

  (it "includes tokens from storage"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should (number? (:tokens data)))))

  (it "computes context-pct as percentage of tokens over context-window"
    (storage/update-tokens! @state-dir "agent:main:cli:direct:testuser" {:inputTokens 3277 :outputTokens 0})
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should (> (:context-pct data) 0))))

  (it "includes cwd"
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should-not-be-nil (:cwd data))))

  (it "includes tool-count from registry"
    (tool-registry/clear!)
    (tool-registry/register! {:name "bash" :description "Run bash" :handler identity})
    (let [ctx  {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          data (bridge/status-data @state-dir "agent:main:cli:direct:testuser" ctx)]
      (should= 1 (:tool-count data)))))

(describe "bridge/format-status"
  (it "formats status map as a markdown table"
    (let [data   {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768
                  :session-key "agent:main:cli:direct:testuser" :session-file "abc12345.jsonl"
                  :soul "You are Isaac." :turns 2 :compactions 0 :tokens 5000 :context-pct 15 :tool-count 1
                  :cwd "/tmp/test"}
          output (bridge/format-status data)]
      (should (re-find #"\*\*Session Status\*\*" output))
      (should (re-find #"\| Crew \| main" output))
      (should (re-find #"\| Model \| echo / grover" output))
      (should (re-find #"\| Context \|" output)))))

(describe "bridge/slash-command?"
  (it "returns true for /status"
    (should (bridge/slash-command? "/status")))

  (it "returns true for any slash-prefixed input"
    (should (bridge/slash-command? "/help")))

  (it "returns false for normal input"
    (should-not (bridge/slash-command? "hello world")))

  (it "returns false for blank input"
    (should-not (bridge/slash-command? "")))

  (it "returns false for nil"
    (should-not (bridge/slash-command? nil))))

(describe "bridge/dispatch"
  (with-all state-dir "target/test-state/bridge-dispatch-spec")

  (before
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "agent:main:cli:direct:testuser"))

  (it "returns command type for /status"
    (let [ctx    {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          result (bridge/dispatch @state-dir "agent:main:cli:direct:testuser" "/status" ctx nil)]
      (should= :command (:type result))
      (should= :status (:command result))
      (should-not-be-nil (:data result))))

  (it "includes status data in command result"
    (let [ctx    {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
          result (bridge/dispatch @state-dir "agent:main:cli:direct:testuser" "/status" ctx nil)]
      (should= "main"   (get-in result [:data :crew]))
      (should= "echo"   (get-in result [:data :model]))))

  (it "returns unknown command error for unrecognized slash commands"
    (let [ctx    {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          result (bridge/dispatch @state-dir "agent:main:cli:direct:testuser" "/unknown" ctx nil)]
      (should= :command (:type result))
      (should= :unknown (:command result))))

  (it "delegates to turn-fn for non-slash input"
    (let [ctx        {:agent "main" :model "echo" :provider "grover" :context-window 32768}
          called     (atom nil)
          turn-fn    (fn [input opts] (reset! called {:input input :opts opts}) {:content "hi"})
          result     (bridge/dispatch @state-dir "agent:main:cli:direct:testuser" "hello" ctx turn-fn)]
      (should= :turn (:type result))
      (should= "hello" (:input @called)))))

(describe "bridge/dispatch - /model command"
  (with-all state-dir "target/test-state/bridge-model-spec")

  (before
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "model-test"))

  (it "shows the current model when no argument is given"
    (let [ctx    {:model "echo" :provider "grover" :context-window 32768
                  :models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}}
          result (bridge/dispatch @state-dir "model-test" "/model" ctx nil)]
      (should= :command (:type result))
      (should= :model (:command result))
      (should= "grover (grover/echo) is the current model" (:message result))))

  (it "switches model and returns confirmation message"
    (let [ctx    {:model "echo" :provider "grover" :context-window 32768
                  :models {"grover" {:alias "grover" :model "echo"        :provider "grover" :contextWindow 32768}
                           "grok"   {:alias "grok"   :model "grok-4-1-fast" :provider "grok" :contextWindow 32768}}}
          result (bridge/dispatch @state-dir "model-test" "/model grok" ctx nil)]
      (should= :command (:type result))
      (should= :model (:command result))
      (should= "switched model to grok (grok/grok-4-1-fast)" (:message result))))

  (it "persists the switched model in the session"
    (let [ctx {:model "echo" :provider "grover" :context-window 32768
               :models {"grover" {:alias "grover" :model "echo"        :provider "grover" :contextWindow 32768}
                        "grok"   {:alias "grok"   :model "grok-4-1-fast" :provider "grok" :contextWindow 32768}}}]
      (bridge/dispatch @state-dir "model-test" "/model grok" ctx nil)
      (let [session (storage/get-session @state-dir "model-test")]
        (should= "grok-4-1-fast" (:model session))
        (should= "grok" (:provider session)))))

  (it "returns an error for an unknown model alias"
    (let [ctx    {:model "echo" :provider "grover" :context-window 32768
                  :models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}}
          result (bridge/dispatch @state-dir "model-test" "/model nonexistent" ctx nil)]
      (should= :command (:type result))
      (should= :unknown (:command result))
      (should= "unknown model: nonexistent" (:message result)))))

(describe "bridge/dispatch - /crew command"
  (with-all state-dir "target/test-state/bridge-crew-spec")

  (before
    (delete-dir! @state-dir)
    (storage/create-session! @state-dir "crew-test"))

  (it "shows the current crew when no argument is given"
    (let [ctx    {:crew "main" :crew-members {"main" {} "ketch" {}}}
          result (bridge/dispatch @state-dir "crew-test" "/crew" ctx nil)]
      (should= :command (:type result))
      (should= :crew (:command result))
      (should= "main is the current crew member" (:message result))))

  (it "switches crew and returns confirmation message"
    (let [ctx    {:crew "main" :crew-members {"main" {} "ketch" {}}}
          result (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)]
      (should= :command (:type result))
      (should= :crew (:command result))
      (should= "switched crew to ketch" (:message result))))

  (it "persists the switched crew in the session"
    (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}]
      (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)
      (let [session (storage/get-session @state-dir "crew-test")]
        (should= "ketch" (:crew session))
        (should= "ketch" (:agent session))
        (should= nil (:model session))
        (should= nil (:provider session)))))

  (it "returns an error for an unknown crew name"
    (let [ctx    {:crew "main" :crew-members {"main" {} "ketch" {}}}
          result (bridge/dispatch @state-dir "crew-test" "/crew nonexistent" ctx nil)]
      (should= :command (:type result))
      (should= :unknown (:command result))
      (should= "unknown crew: nonexistent" (:message result)))))
