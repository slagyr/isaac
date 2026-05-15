(ns isaac.bridge.prompt-cli-spec
  (:require
    [clojure.string :as str]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.bridge.prompt-cli :as sut]
    [isaac.drive.turn :as single-turn]
    [isaac.fs :as fs]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "/test/prompt"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(defn- fake-process! [text]
  (fn [key-str _input opts]
    (comm/on-text-chunk (:comm opts) key-str text)
    {}))

(describe "CLI Prompt"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example] (helper/with-memory-store (binding [fs/*fs* (fs/mem-fs)] (example))))

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

  (describe "run"

    (it "returns 1 and mentions 'required' when --message is missing"
      (let [output (with-out-str
                     (should= 1 (sut/run base-opts)))]
        (should (str/includes? output "required"))))

    (it "accepts a positional message through run-fn"
      (let [captured (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [key-str input opts]
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
        (with-redefs [single-turn/run-turn! (fn [key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Hi")
                                                        {})]
          (with-out-str (sut/run (assoc base-opts :message "Hi"))))
        (should= "prompt-default" @used-key)))

    (it "uses --session when provided"
      (helper/create-session! "/test/prompt" "agent:main:cli:direct:user1")
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
           (with-out-str
             (sut/run (assoc base-opts :message "Next" :session "agent:main:cli:direct:user1"))))
        (should= "agent:main:cli:direct:user1" @used-key)))

    (it "uses the stored session crew when --session is provided without --crew"
      (helper/create-session! "/test/prompt" "agent:main:cli:direct:user1" {:crew "ketch"})
      (let [captured (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_ _ opts]
                                              (reset! captured opts)
                                              (comm/on-text-chunk (:comm opts) "agent:main:cli:direct:user1" "Ok")
                                              {})]
          (with-out-str
            (sut/run {:state-dir "/test/prompt"
                      :message   "Next"
                      :session   "agent:main:cli:direct:user1"
                      :agents    {"main"  {:name "main" :soul "You are Isaac." :model "grover"}
                                  "ketch" {:name "ketch" :soul "You are a pirate." :model "grover2"}}
                      :models    {"grover"  {:alias "grover" :model "echo" :provider "grover" :context-window 32768}
                                  "grover2" {:alias "grover2" :model "echo-alt" :provider "grover" :context-window 16384}}})))
        (should= "echo-alt" (:model @captured))
        (should= "You are a pirate." (:soul @captured))))

    (it "lets --crew override the stored session crew"
      (helper/create-session! "/test/prompt" "agent:main:cli:direct:user1" {:crew "ketch"})
      (let [captured (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [_ _ opts]
                                              (reset! captured opts)
                                              (comm/on-text-chunk (:comm opts) "agent:main:cli:direct:user1" "Ok")
                                              {})]
          (with-out-str
            (sut/run {:state-dir "/test/prompt"
                      :message   "Next"
                      :session   "agent:main:cli:direct:user1"
                      :crew      "main"
                      :agents    {"main"  {:name "main" :soul "You are Isaac." :model "grover"}
                                  "ketch" {:name "ketch" :soul "You are a pirate." :model "grover2"}}
                      :models    {"grover"  {:alias "grover" :model "echo" :provider "grover" :context-window 32768}
                                  "grover2" {:alias "grover2" :model "echo-alt" :provider "grover" :context-window 16384}}})))
        (should= "echo" (:model @captured))
        (should= "You are Isaac." (:soul @captured))))

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
        (with-redefs [single-turn/run-turn! (fn [key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "recent" @used-key)))

    (it "--resume creates a new session when none exist"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/run-turn! (fn [key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:comm opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "prompt-default" @used-key)))

    )
  )
