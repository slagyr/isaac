(ns isaac.bridge-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.bridge :as bridge]
    [isaac.spec-helper :as helper]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(defn- delete-dir! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [file (reverse (file-seq f))]
        (.delete file)))))

(def state-dir (atom nil))

(describe "bridge"
  (context "status-data"
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "testuser"))

    (it "includes crew, model, provider from context"
      (let [ctx {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= "main" (:crew data))
        (should= "echo" (:model data))
        (should= "grover" (:provider data))))

    (it "includes context-window from context"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= 32768 (:context-window data))))

    (it "includes session-key"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= "testuser" (:session-key data))))

    (it "includes session-file from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should-not-be-nil (:session-file data))
        (should (re-matches #"[a-z0-9-]+\.jsonl" (:session-file data)))))

    (it "counts zero turns on a fresh session"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= 0 (:turns data))))

    (it "counts turns from transcript messages"
      (helper/append-message! @state-dir "testuser" {:role "user" :content "hello"})
      (helper/append-message! @state-dir "testuser" {:role "assistant" :content "hi"})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= 2 (:turns data))))

    (it "includes compaction count from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= 0 (:compactions data))))

    (it "includes tokens from storage"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should (number? (:tokens data)))))

    (it "computes context-pct as percentage of tokens over context-window"
      (helper/update-tokens! @state-dir "testuser" {:input-tokens 3277 :output-tokens 0})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should (> (:context-pct data) 0))))

    (it "includes cwd"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should-not-be-nil (:cwd data))))

    (it "includes tool-count from registry"
      (tool-registry/clear!)
      (tool-registry/register! {:name "bash" :description "Run bash" :handler identity})
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            data (bridge/status-data @state-dir "testuser" ctx)]
        (should= 1 (:tool-count data))))
    )

  (context "format-status"
    (it "formats status map as a markdown table"
      (let [data {:boot-files "Micah's AI assistant management tools. This project uses toolbox to manage agent components."
                  :crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768
                  :session-key "testuser" :session-file "abc12345.jsonl"
                  :soul "You are Isaac." :turns 2 :compactions 2 :tokens 5000 :context-pct 15 :tool-count 1
                  :cwd "/tmp/test"}
            output (bridge/format-status data)]
        (should (re-find #"```text" output))
        (should (re-find #"Session Status" output))
        (should (re-find #"Crew\s+main" output))
        (should (re-find #"─+" output))
        (should (re-find #"Model\s+echo \(grover\)" output))
        (should (re-find #"Compactions\s+2" output))
        (should (re-find #"Context\s+5,000 / 32,768 \(15%\)" output))
        (should (re-find #"Soul\s+\"Micah's AI assistant management tools\. This project uses ...\"" output))
        (should (re-find #"```$" output))
        (should-not (re-find #"You are Isaac\." output))))
    )

  (context "slash-command?"
    (it "returns true for /status"
      (should (bridge/slash-command? "/status")))

    (it "returns true for any slash-prefixed input"
      (should (bridge/slash-command? "/help")))

    (it "returns false for normal input"
      (should-not (bridge/slash-command? "hello world")))

    (it "returns false for blank input"
      (should-not (bridge/slash-command? "")))

    (it "returns false for nil"
      (should-not (bridge/slash-command? nil)))
    )

  (context "dispatch"
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-dispatch-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "testuser"))

    (it "returns command type for /status"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/status" ctx nil)]
        (should= :command (:type result))
        (should= :status (:command result))
        (should-not-be-nil (:data result))))

    (it "includes status data in command result"
      (let [ctx {:crew "main" :agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/status" ctx nil)]
        (should= "main" (get-in result [:data :crew]))
        (should= "echo" (get-in result [:data :model]))))

    (it "returns unknown command error for unrecognized slash commands"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            result (bridge/dispatch @state-dir "testuser" "/unknown" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))))

    (it "delegates to turn-fn for non-slash input"
      (let [ctx {:agent "main" :model "echo" :provider "grover" :context-window 32768}
            called (atom nil)
            turn-fn (fn [input opts] (reset! called {:input input :opts opts}) {:content "hi"})
            result (bridge/dispatch @state-dir "testuser" "hello" ctx turn-fn)]
        (should= :turn (:type result))
        (should= "hello" (:input @called))))
    )

  (context "dispatch - /model command"
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-model-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "model-test"))

    (it "shows the current model when no argument is given"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}}
            result (bridge/dispatch @state-dir "model-test" "/model" ctx nil)]
        (should= :command (:type result))
        (should= :model (:command result))
        (should= "grover (grover/echo) is the current model" (:message result))))

    (it "switches model and returns confirmation message"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}
                          "grok" {:alias "grok" :model "grok-4-1-fast" :provider "grok" :context-window 32768}}}
            result (bridge/dispatch @state-dir "model-test" "/model grok" ctx nil)]
        (should= :command (:type result))
        (should= :model (:command result))
        (should= "switched model to grok (grok/grok-4-1-fast)" (:message result))))

    (it "persists the switched model in the session"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}
                          "grok" {:alias "grok" :model "grok-4-1-fast" :provider "grok" :context-window 32768}}}]
        (bridge/dispatch @state-dir "model-test" "/model grok" ctx nil)
        (let [session (helper/get-session @state-dir "model-test")]
          (should= "grok-4-1-fast" (:model session))
          (should= "grok" (:provider session)))))

    (it "returns an error for an unknown model alias"
      (let [ctx {:model "echo" :provider "grover" :context-window 32768
                 :models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}}
            result (bridge/dispatch @state-dir "model-test" "/model nonexistent" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))
        (should= "unknown model: nonexistent" (:message result))))
    )

  (context "dispatch - /crew command"
    (before
      (reset! state-dir (str (System/getProperty "user.dir") "/target/test-state/bridge-crew-spec-" (random-uuid)))
      (delete-dir! @state-dir)
      (helper/create-session! @state-dir "crew-test"))

    (it "shows the current crew when no argument is given"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew" ctx nil)]
        (should= :command (:type result))
        (should= :crew (:command result))
        (should= "main is the current crew member" (:message result))))

    (it "switches crew and returns confirmation message"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)]
        (should= :command (:type result))
        (should= :crew (:command result))
        (should= "switched crew to ketch" (:message result))))

    (it "logs crew changes when switching to a known crew"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}]
        (log/capture-logs
          (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)
          (let [entry (last @log/captured-logs)]
            (should= :session/crew-changed (:event entry))
            (should= "crew-test" (:session entry))
            (should= "main" (:from entry))
            (should= "ketch" (:to entry))))))

    (it "persists the switched crew in the session"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}]
        (bridge/dispatch @state-dir "crew-test" "/crew ketch" ctx nil)
        (let [session (helper/get-session @state-dir "crew-test")]
          (should= "ketch" (:crew session))
          (should-not (contains? session :agent))
          (should= nil (:model session))
          (should= nil (:provider session)))))

    (it "returns an error for an unknown crew name"
      (let [ctx {:crew "main" :crew-members {"main" {} "ketch" {}}}
            result (bridge/dispatch @state-dir "crew-test" "/crew nonexistent" ctx nil)]
        (should= :command (:type result))
        (should= :unknown (:command result))
        (should= "unknown crew: nonexistent" (:message result))))
    )

  (context "cancellation"
    (it "cancels an active turn and runs cancel hooks"
      (let [called? (atom false)
            turn (bridge/begin-turn! "cancel-test")]
        (bridge/on-cancel! "cancel-test" #(reset! called? true))
        (bridge/cancel! "cancel-test")
        (should @called?)
        (should (bridge/cancelled? "cancel-test"))
        (bridge/end-turn! "cancel-test" turn)))

    (it "applies a pending cancel to the next turn"
      (bridge/cancel! "cancel-later")
      (let [turn (bridge/begin-turn! "cancel-later")]
        (should (bridge/cancelled? "cancel-later"))
        (bridge/end-turn! "cancel-later" turn)
        (should-not (bridge/cancelled? "cancel-later"))))
    )

  (context "resolve-turn-opts"
    (def test-cfg {:defaults  {:crew "main" :model "fast"}
                   :crew      {"main" {:soul "You are a test."}}
                   :models    {"fast"  {:model "gpt-4o-mini" :provider "openai" :context-window 16000}
                               "smart" {:model "gpt-4o"      :provider "openai" :context-window 128000}}
                   :providers {"openai" {:api "openai"}}})

    (around [it]
      (let [resolve* requiring-resolve]
        (config/set-snapshot! test-cfg)
        (with-redefs [clojure.core/requiring-resolve
                      (fn [sym]
                        (case sym
                          isaac.drive.dispatch/make-provider
                          (fn [provider-id _cfg] {:id provider-id})
                          (resolve* sym)))]
          (it))
        (config/set-snapshot! nil)))

    (it "resolves soul from ambient config"
      (let [result (bridge/resolve-turn-opts {:crew-id "main"})]
        (should= "You are a test." (:soul result))))

    (it "resolves model via crew defaults"
      (let [result (bridge/resolve-turn-opts {:crew-id "main"})]
        (should= "gpt-4o-mini" (:model result))))

    (it "resolves context-window from model config"
      (let [result (bridge/resolve-turn-opts {:crew-id "main"})]
        (should= 16000 (:context-window result))))

    (it "passes comm through unchanged"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :comm :test-comm})]
        (should= :test-comm (:comm result))))

    (it "prepends soul-prepend to soul"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :soul-prepend "CONTEXT"})]
        (should (str/includes? (:soul result) "CONTEXT"))
        (should (str/includes? (:soul result) "You are a test."))))

    (it "overrides model via model-ref alias"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :model-ref "smart"})]
        (should= "gpt-4o" (:model result))
        (should= 128000 (:context-window result))))

    (it "overrides model via provider/model string"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :model-ref "xai/grok-3"})]
        (should= "grok-3" (:model result))))

    (it "pre-resolved :model wins over crew default"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :model "pre-resolved-model"})]
        (should= "pre-resolved-model" (:model result))))

    (it "pre-resolved :context-window wins over crew config"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :context-window 99999})]
        (should= 99999 (:context-window result))))

    (it "pre-resolved :soul wins over crew soul"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :soul "Override soul"})]
        (should= "Override soul" (:soul result))))

    (it "converts string :provider to a Provider instance"
      (let [result (bridge/resolve-turn-opts {:crew-id "main" :provider "openai"})]
        (should= {:id "openai"} (:provider result))))

    (it "passes a Provider instance through unchanged"
      (let [fake-provider {:id "already-resolved"}
            result        (bridge/resolve-turn-opts {:crew-id "main" :provider fake-provider})]
        (should= fake-provider (:provider result))))

    (it "reads crew config from explicit :cfg when snapshot is not set"
      (config/set-snapshot! nil)
      (let [explicit-cfg {:crew   {"main" {:soul "From cfg"}}
                          :models {"fast" {:model "gpt-4o-mini" :provider "openai" :context-window 16000}}}
            result       (bridge/resolve-turn-opts {:crew-id "main" :cfg explicit-cfg})]
        (should= "From cfg" (:soul result))))
    )
  )
