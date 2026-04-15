(ns isaac.cli.prompt-spec
  (:require
    [clojure.string :as str]
    [isaac.channel :as channel]
    [isaac.config.resolution :as config]
    [isaac.cli.prompt :as sut]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.session.fs :as fs]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "target/test-prompt"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}})

(defn- fake-process! [text]
  (fn [_sdir key-str _input opts]
    (channel/on-text-chunk (:channel opts) key-str text)
    {}))

(describe "CLI Prompt"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "resolve-run-opts"

    (it "resolves the selected crew member's model and soul from config"
      (with-redefs [config/load-config (fn [& _]
                                         {:crew   {:defaults {:model "grover"}
                                                   :list     [{:id "main" :soul "You are Isaac." :model "grover"}
                                                              {:id "ketch" :soul "You are a pirate." :model "grover2"}]
                                                   :models   {:grover  {:model "echo" :provider "grover" :contextWindow 32768}
                                                              :grover2 {:model "echo-alt" :provider "grover" :contextWindow 16384}}}
                                          :models {:providers [{:name "grover" :baseUrl "http://fake"}]}})]
        (let [result (@#'sut/resolve-run-opts {:crew "ketch" :home "/tmp/test-home"})]
          (should= "ketch" (:agent-id result))
          (should= "You are a pirate." (:soul result))
          (should= "echo-alt" (:model result))
          (should= "grover" (:provider result))
          (should= 16384 (:context-window result)))))

    (it "falls back to workspace SOUL.md for the selected crew member"
      (with-redefs [config/load-config        (fn [& _]
                                                {:crew   {:defaults {:model "grover"}
                                                          :list     [{:id "main" :soul "You are Isaac." :model "grover"}
                                                                     {:id "ketch" :model "grover"}]
                                                          :models   {:grover {:model "echo" :provider "grover" :contextWindow 32768}}}
                                                 :models {:providers [{:name "grover" :baseUrl "http://fake"}]}})
                    config/read-workspace-file (fn [crew-id filename opts]
                                                 (when (and (= "ketch" crew-id)
                                                            (= "SOUL.md" filename)
                                                            (= "/tmp/test-home" (:home opts)))
                                                   "Workspace pirate soul"))]
        (let [result (@#'sut/resolve-run-opts {:crew "ketch" :home "/tmp/test-home"})]
          (should= "Workspace pirate soul" (:soul result))))))

  (describe "run"

    (it "returns 1 and mentions 'required' when --message is missing"
      (let [output (with-out-str
                     (should= 1 (sut/run base-opts)))]
        (should (str/includes? output "required"))))

    (it "prints the response text and returns 0"
      (with-redefs [single-turn/process-user-input! (fake-process! "Test response")]
        (let [output (with-out-str
                       (should= 0 (sut/run (assoc base-opts :message "Hello"))))]
          (should (str/includes? output "Test response")))))

    (it "uses prompt-default as the default session"
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (channel/on-text-chunk (:channel opts) key-str "Hi")
                                                        {})]
          (with-out-str (sut/run (assoc base-opts :message "Hi"))))
        (should= "prompt-default" @used-key)))

    (it "uses --session when provided"
      (storage/create-session! "target/test-prompt" "agent:main:cli:direct:user1")
      (let [used-key (atom nil)]
        (with-redefs [single-turn/process-user-input! (fn [_sdir key-str _input opts]
                                                        (reset! used-key key-str)
                                                        (channel/on-text-chunk (:channel opts) key-str "Ok")
                                                        {})]
          (with-out-str
            (sut/run (assoc base-opts :message "Next" :session "agent:main:cli:direct:user1"))))
        (should= "agent:main:cli:direct:user1" @used-key)))

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

    )
  )
