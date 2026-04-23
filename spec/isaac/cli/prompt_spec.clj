(ns isaac.cli.prompt-spec
  (:require
    [clojure.string :as str]
    [isaac.comm :as comm]
    [isaac.config.loader :as config]
    [isaac.cli.prompt :as sut]
    [isaac.drive.turn :as single-turn]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "/test/prompt"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(defn- fake-process! [text]
  (fn [_sdir key-str _input opts]
    (comm/on-text-chunk (:channel opts) key-str text)
    {}))

(describe "CLI Prompt"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "resolve-run-opts"

    (it "resolves the selected crew member's model and soul from config"
      (with-redefs [config/load-config (fn [& _]
                                         {:defaults  {:crew "main" :model "grover"}
                                          :crew      {"main" {:soul "You are Isaac." :model "grover"}
                                                      "ketch" {:soul "You are a pirate." :model "grover2"}}
                                           :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}
                                                       "grover2" {:model "echo-alt" :provider "grover" :context-window 16384}}
                                           :providers {"grover" {:base-url "http://fake"}}})]
        (let [result (@#'sut/resolve-run-opts {:crew "ketch" :home "/tmp/test-home"})]
          (should= "ketch" (:agent-id result))
          (should= "You are a pirate." (:soul result))
          (should= "echo-alt" (:model result))
          (should= "grover" (:provider result))
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
          (should= "Workspace pirate soul" (:soul result))))))

  (describe "run"

    (it "returns 1 and mentions 'required' when --message is missing"
      (let [output (with-out-str
                     (should= 1 (sut/run base-opts)))]
        (should (str/includes? output "required"))))

    (it "accepts a positional message through run-fn"
      (let [captured (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str input opts]
                                                        (reset! captured {:input input :opts opts})
                                                        (comm/on-text-chunk (:channel opts) key-str "Hi back")
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
      (with-redefs [single-turn/process-user-input! (fake-process! "Test response")]
        (let [output (with-out-str
                       (should= 0 (sut/run (assoc base-opts :message "Hello"))))]
          (should (str/includes? output "Test response")))))

    (it "passes configured crew tools into the prompt turn"
      (let [captured (atom nil)
            opts     (assoc-in base-opts [:agents "main" :tools :allow] [:read :write :exec])]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input turn-opts]
                                                        (reset! captured turn-opts)
                                                        (comm/on-text-chunk (:channel turn-opts) key-str "Test response")
                                                        {})]
          (with-out-str
            (should= 0 (sut/run (assoc opts :message "Hello")))))
        (should= [:read :write :exec]
                 (get-in @captured [:crew-members "main" :tools :allow]))))

    (it "uses prompt-default as the default session"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:channel opts) key-str "Hi")
                                                        {})]
          (with-out-str (sut/run (assoc base-opts :message "Hi"))))
        (should= "prompt-default" @used-key)))

    (it "uses --session when provided"
      (storage/create-session! "/test/prompt" "agent:main:cli:direct:user1")
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:channel opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Next" :session "agent:main:cli:direct:user1"))))
        (should= "agent:main:cli:direct:user1" @used-key)))

    (it "stores cwd on a newly created prompt session"
      (with-redefs [single-turn/process-user-input! (fake-process! "Hello")]
        (with-out-str
          (sut/run (assoc base-opts :message "Hi")))
        (let [session (storage/get-session "/test/prompt" "prompt-default")]
          (should= (System/getProperty "user.dir") (:cwd session)))))

    (it "outputs JSON when --json is set"
      (with-redefs [single-turn/process-user-input! (fake-process! "Hello")]
        (let [output (with-out-str
                       (sut/run (assoc base-opts :message "Hi" :json true)))]
          (should (str/includes? output "\"response\""))
          (should (str/includes? output "Hello")))))

    (it "returns 1 when process-user-input! returns an error"
      (with-redefs [single-turn/process-user-input! (fn [& _] {:error {:message "context length exceeded"}})]
        (binding [*err* (java.io.StringWriter.)]
          (with-out-str
            (should= 1 (sut/run (assoc base-opts :message "Hi")))))))

    (it "prints provider errors to stderr"
      (with-redefs [single-turn/process-user-input! (fn [& _] {:error :api-error :message "context length exceeded"})]
        (let [err-writer (java.io.StringWriter.)]
          (binding [*err* err-writer]
            (with-out-str
              (should= 1 (sut/run (assoc base-opts :message "Hi")))))
          (should (str/includes? (str err-writer) "context length exceeded")))))

    (it "--resume uses the most recent session"
      (storage/create-session! "/test/prompt" "older"  {:cwd "/test/prompt" :updatedAt "2026-04-10T10:00:00"})
      (storage/create-session! "/test/prompt" "recent" {:cwd "/test/prompt" :updatedAt "2026-04-12T15:00:00"})
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:channel opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "recent" @used-key)))

    (it "--resume creates a new session when none exist"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (comm/on-text-chunk (:channel opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Hi" :resume true))))
        (should= "prompt-default" @used-key)))

    )
  )
