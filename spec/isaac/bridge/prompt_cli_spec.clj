(ns isaac.bridge.prompt-cli-spec
  (:require
    [clojure.string :as str]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.bridge.prompt-cli :as sut]
    [isaac.drive.turn :as single-turn]
    [isaac.fs :as fs]
    [isaac.session.context :as session-ctx]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "/test/prompt"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(defn- fake-process! [text]
  (fn [_sdir key-str _input opts]
    (comm/on-text-chunk (:comm opts) key-str text)
    {}))

(describe "CLI Prompt"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example] (binding [fs/*fs* (fs/mem-fs)] (example)))

  (describe "CollectorChannel"

    (it "renders compaction lifecycle and tool events to stderr while keeping response text separate"
      (let [collector   (#'sut/make-collector)
            channel     (:comm collector)
            err-writer  (java.io.StringWriter.)]
        (binding [*err* err-writer]
          (comm/on-compaction-start channel "prompt-default" {:total-tokens 95})
          (comm/on-tool-call channel "prompt-default" {:id "tc" :name "grep" :arguments {:pattern "lettuce" :path "src"}})
          (comm/on-tool-result channel "prompt-default" {:id "tc" :name "grep" :arguments {:pattern "lettuce" :path "src"}} "ok")
          (comm/on-compaction-success channel "prompt-default" {:tokens-saved 40})
          (comm/on-compaction-failure channel "prompt-default" {:error :llm-error :consecutive-failures 2})
          (comm/on-compaction-disabled channel "prompt-default" {:reason :too-many-failures})
          (comm/on-text-chunk channel "prompt-default" "here is the answer"))
        (should= "here is the answer" @(:text collector))
        (let [stderr (str err-writer)]
          (should (str/includes? stderr "🥬 compacting"))
          (should (str/includes? stderr "95"))
          (should (str/includes? stderr "🔍 grep"))
          (should (str/includes? stderr "lettuce"))
          (should (str/includes? stderr "← grep"))
          (should (str/includes? stderr "✨ compacted"))
          (should (str/includes? stderr "🥀 compaction failed"))
          (should (str/includes? stderr "llm-error"))
          (should (str/includes? stderr "🪦 compaction disabled"))
          (should (str/includes? stderr "too-many-failures"))))))

  (describe "tool-icon"

    (it "renders distinct icons for the known built-in tools"
      (should= "🔍" (@#'sut/tool-icon "grep"))
      (should= "📖" (@#'sut/tool-icon "read"))
      (should= "✏️" (@#'sut/tool-icon "write"))
      (should= "✏️" (@#'sut/tool-icon "edit"))
      (should= "⚙️" (@#'sut/tool-icon "exec"))
      (should= "🌐" (@#'sut/tool-icon "web_fetch"))
      (should= "💾" (@#'sut/tool-icon "memory_save"))
      (should= "🧰" (@#'sut/tool-icon "unknown"))))

  (describe "resolve-run-opts"

    (describe "resolve-provider-instance"

      (it "uses the provider from the base context when present"
        (let [provider :existing]
          (should= {:alias-match nil :parsed nil :provider provider}
                   (#'sut/resolve-provider-instance {:provider provider} nil {} nil {}))))

      (it "creates a provider from a configured alias match"
        (let [resolve* requiring-resolve]
          (with-redefs [clojure.core/requiring-resolve (fn [sym]
                                                         (if (= sym 'isaac.drive.dispatch/make-provider)
                                                           (fn [provider-id provider-cfg]
                                                             {:id provider-id :cfg provider-cfg})
                                                           (resolve* sym)))]
            (should= {:alias-match {:provider "grover"}
                      :parsed      nil
                      :provider    {:id "grover" :cfg {:api "grover" :auth "none" :models []}}}
                     (#'sut/resolve-provider-instance {}
                                                     "grover"
                                                     {"grover" {:provider "grover"}}
                                                     nil
                                                     {})))))

      (it "falls back to an ollama provider when neither context nor model ref provide one"
        (let [resolve* requiring-resolve]
          (with-redefs [clojure.core/requiring-resolve (fn [sym]
                                                         (if (= sym 'isaac.drive.dispatch/make-provider)
                                                           (fn [provider-id provider-cfg]
                                                             {:id provider-id :cfg provider-cfg})
                                                           (resolve* sym)))]
            (should= {:alias-match nil :parsed nil :provider {:id "ollama" :cfg {}}}
                     (#'sut/resolve-provider-instance {}
                                                     nil
                                                     {}
                                                     nil
                                                     {}))))))

    (it "resolves the selected crew member's model and soul from config"
      (with-redefs [config/load-config (fn [& _]
                                         {:defaults  {:crew "main" :model "grover"}
                                          :crew      {"main" {:soul "You are Isaac." :model "grover"}
                                                      "ketch" {:soul "You are a pirate." :model "grover2"}}
                                           :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}
                                                       "grover2" {:model "echo-alt" :provider "grover" :context-window 16384}}
                                           :providers {"grover" {:base-url "http://fake"}}})]
        (let [result (@#'sut/resolve-run-opts {:crew "ketch" :home "/tmp/test-home"})]
          (should= "ketch" (:crew-id result))
          (should= "You are a pirate." (:soul result))
          (should= "echo-alt" (:model result))
          (should= "grover" ((requiring-resolve 'isaac.llm.api/display-name) (:provider result)))
          (should= 16384 (:context-window result)))))

    (it "falls back to workspace SOUL.md for the selected crew member"
      (fs/spit "/tmp/test-home/.isaac/workspace-ketch/SOUL.md" "Workspace pirate soul")
      (with-redefs [config/load-config (fn [& _]
                                         {:defaults  {:crew "main" :model "grover"}
                                          :crew      {"main" {:soul "You are Isaac." :model "grover"}
                                                      "ketch" {:model "grover"}}
                                           :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                                            :providers {"grover" {:base-url "http://fake"}}})]
        (let [result (@#'sut/resolve-run-opts {:crew "ketch" :home "/tmp/test-home"})]
          (should= "Workspace pirate soul" (:soul result)))))

    (it "uses injected crew and models when opts provide them directly"
      (let [resolve* requiring-resolve
            captured (atom nil)]
        (with-redefs [config/load-config              (fn [& _] {})
                      config/normalize-config         identity
                      config/set-snapshot!            (fn [_] nil)
                      session-ctx/resolve-turn-context (fn [context crew-id]
                                                         (reset! captured [context crew-id])
                                                         {:soul "Injected soul" :model "echo-direct" :context-window 8192})
                      clojure.core/requiring-resolve  (fn [sym]
                                                        (if (= sym 'isaac.drive.dispatch/make-provider)
                                                          (fn [provider-id provider-cfg]
                                                            {:id provider-id :cfg provider-cfg})
                                                          (resolve* sym)))]
          (let [result (@#'sut/resolve-run-opts {:home   "/tmp/test-home"
                                                 :crew   {"main" {:model "grover" :soul "Injected soul"}}
                                                 :models {"grover" {:model "echo-direct" :provider "grover" :context-window 8192}}})]
            (should= [{:crew-members {"main" {:model "grover" :soul "Injected soul"}}
                        :home         "/tmp/test-home"
                        :models       {"grover" {:model "echo-direct" :provider "grover" :context-window 8192}}}
                       "main"]
                      @captured)
            (should= "Injected soul" (:soul result))
            (should= "echo-direct" (:model result))
            (should= 8192 (:context-window result)))))

    (it "uses explicit provider-configs for parsed provider/model overrides"
      (let [resolve* requiring-resolve]
        (with-redefs [config/load-config              (fn [& _] {:defaults {:crew "main" :model "grover"}
                                                                 :crew     {"main" {:soul "You are Isaac." :model "grover"}}
                                                                 :models   {"grover" {:model "echo" :provider "grover" :context-window 32768}}})
                      config/normalize-config         identity
                      config/parse-model-ref          (fn [_] {:provider "anthropic" :model "claude-sonnet"})
                      session-ctx/resolve-turn-context (fn [_ _] {:soul "You are Isaac." :model "echo" :context-window 32768})
                      clojure.core/requiring-resolve  (fn [sym]
                                                        (if (= sym 'isaac.drive.dispatch/make-provider)
                                                          (fn [provider-id provider-cfg]
                                                            {:id provider-id :cfg provider-cfg})
                                                          (resolve* sym)))]
          (let [result (@#'sut/resolve-run-opts {:home             "/tmp/test-home"
                                                 :model            "anthropic/claude-sonnet"
                                                 :provider-configs {"anthropic" {:api-key "sk-test"}}})]
            (should= "claude-sonnet" (:model result))
            (should= "anthropic" (get-in result [:provider :id]))
            (should= "sk-test" (get-in result [:provider :cfg :api-key]))
            (should= 32768 (:context-window result))))))

    (it "falls back to an ollama provider when context and overrides provide none"
      (let [resolve* requiring-resolve]
        (with-redefs [config/load-config              (fn [& _] {})
                      config/normalize-config         identity
                      session-ctx/resolve-turn-context (fn [_ _] {:soul "You are Isaac." :model "echo"})
                      clojure.core/requiring-resolve  (fn [sym]
                                                        (if (= sym 'isaac.drive.dispatch/make-provider)
                                                          (fn [provider-id provider-cfg]
                                                            {:id provider-id :cfg provider-cfg})
                                                          (resolve* sym)))]
          (let [result (@#'sut/resolve-run-opts {:home "/tmp/test-home"})]
            (should= "ollama" (get-in result [:provider :id]))
            (should= {} (get-in result [:provider :cfg]))
            (should= 32768 (:context-window result))))))

    (it "keeps the provider from the resolved context and uses stateDir from config"
      (with-redefs [config/load-config              (fn [& _] {:stateDir "/tmp/state"
                                                               :models   {"grover" {:model "echo" :provider "grover" :context-window 4096}}})
                    config/normalize-config         identity
                    session-ctx/resolve-turn-context (fn [_ _] {:soul "You are Isaac." :model "echo" :provider :existing-provider :context-window 4096})]
        (let [result (@#'sut/resolve-run-opts {:home "/tmp/test-home"})]
          (should= :existing-provider (:provider result))
          (should= "/tmp/state" (:state-dir result))
          (should= 4096 (:context-window result)))))

    (it "resolves keyword model aliases from injected models"
      (let [resolve* requiring-resolve]
        (with-redefs [config/load-config              (fn [& _] {})
                      config/normalize-config         identity
                      session-ctx/resolve-turn-context (fn [_ _] {:soul "Injected soul" :model "echo" :context-window 2048})
                      clojure.core/requiring-resolve  (fn [sym]
                                                        (if (= sym 'isaac.drive.dispatch/make-provider)
                                                          (fn [provider-id provider-cfg]
                                                            {:id provider-id :cfg provider-cfg})
                                                          (resolve* sym)))]
          (let [result (@#'sut/resolve-run-opts {:home   "/tmp/test-home"
                                                 :crew   {"main" {:model :grover :soul "Injected soul"}}
                                                 :models {:grover {:model "echo" :provider "grover" :context-window 2048}}
                                                 :model  "grover"})]
            (should= "echo" (:model result))
            (should= "grover" (get-in result [:provider :id]))
            (should= 2048 (:context-window result))))))

    )

  )

  (describe "run"

    (it "returns 1 and mentions 'required' when --message is missing"
      (let [output (with-out-str
                     (should= 1 (sut/run base-opts)))]
        (should (str/includes? output "required"))))

    (it "accepts a positional message through run-fn"
      (let [captured (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_sdir key-str input opts]
                                                        (reset! captured {:input input :opts opts})
                                                        (comm/on-text-chunk (:comm opts) key-str "Hi back")
                                                        {})]
          (with-out-str
            (should= 0 (sut/run-fn (assoc base-opts :_raw-args ["Hello there"]))))
          (should= "Hello there" (:input @captured)))))

    (it "fails clearly when no config exists"
      (let [err (java.io.StringWriter.)]
        (binding [*err* err]
          (with-out-str
            (should= 1 (sut/run {:home "/tmp/missing-config" :message "Hi"}))))
        (should (str/includes? (str err) "no config found"))
        (should (str/includes? (str err) "/tmp/missing-config/.isaac/config/isaac.edn"))))

    (it "prints the response text and returns 0"
      (with-redefs [single-turn/run-turn! (fake-process! "Test response")]
        (let [output (with-out-str
                       (should= 0 (sut/run (assoc base-opts :message "Hello"))))]
          (should (str/includes? output "Test response")))))

    (it "uses prompt-default as the default session"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Hi")
                                                        {})]
          (with-out-str (sut/run (assoc base-opts :message "Hi"))))
        (should= "prompt-default" @used-key)))

    (it "uses --session when provided"
      (helper/create-session! "/test/prompt" "agent:main:cli:direct:user1")
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Next" :session "agent:main:cli:direct:user1"))))
        (should= "agent:main:cli:direct:user1" @used-key)))

    (it "stores cwd on a newly created prompt session"
      (with-redefs [single-turn/run-turn! (fake-process! "Hello")]
        (with-out-str
          (sut/run (assoc base-opts :message "Hi")))
        (let [session (helper/get-session "/test/prompt" "prompt-default")]
          (should= (System/getProperty "user.dir") (:cwd session)))))

    (it "writes only crew when creating a fresh prompt session"
      (with-redefs [single-turn/run-turn! (fake-process! "Hello")]
        (with-out-str
          (sut/run (assoc base-opts :message "Hi" :session "fresh-prompt")))
        (let [session (helper/get-session "/test/prompt" "fresh-prompt")]
          (should= "main" (:crew session))
          (should-not (contains? session :agent)))))

    (it "outputs JSON when --json is set"
      (with-redefs [single-turn/run-turn! (fake-process! "Hello")]
        (let [output (with-out-str
                       (sut/run (assoc base-opts :message "Hi" :json true)))]
          (should (str/includes? output "\"response\""))
          (should (str/includes? output "Hello")))))

    (it "returns 1 when run-turn! returns an error"
      (with-redefs [single-turn/run-turn! (fn [& _] {:error {:message "context length exceeded"}})]
        (binding [*err* (java.io.StringWriter.)]
          (with-out-str
            (should= 1 (sut/run (assoc base-opts :message "Hi")))))))

    (it "prints provider errors to stderr"
      (with-redefs [single-turn/run-turn! (fn [& _] {:error :api-error :message "context length exceeded"})]
        (let [err-writer (java.io.StringWriter.)]
          (binding [*err* err-writer]
            (with-out-str
              (should= 1 (sut/run (assoc base-opts :message "Hi")))))
          (should (str/includes? (str err-writer) "context length exceeded")))))

    (it "--resume uses the most recent session"
      (helper/create-session! "/test/prompt" "older"  {:cwd "/test/prompt" :updated-at "2026-04-10T10:00:00"})
      (helper/create-session! "/test/prompt" "recent" {:cwd "/test/prompt" :updated-at "2026-04-12T15:00:00"})
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "recent" @used-key)))

    (it "--resume creates a new session when none exist"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "prompt-default" @used-key)))

    )
  )
