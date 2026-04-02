(ns isaac.cli.chat-spec
  (:require
    [clojure.java.io :as io]
    [isaac.cli.chat :as sut]
    [isaac.config.resolution :as config]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def test-dir "target/test-chat")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "CLI Chat"

  (before-all (clean-dir! test-dir))
  (after (clean-dir! test-dir))

  (describe "prepare"

    (it "returns a context map with defaults"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name    "ollama"
                                       :baseUrl "http://localhost:11434"
                                       :api     "ollama"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "main" (:agent ctx))
        (should= "qwen3-coder:30b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= test-dir (:state-dir ctx))
        (should-not-be-nil (:session-key ctx))
        (should-contain "You are Isaac" (:soul ctx))))

    (it "resolves model override"
      (let [cfg {:agents {:defaults {:model "ollama/default:7b"}}
                 :models {:providers [{:name    "ollama"
                                       :baseUrl "http://localhost:11434"
                                       :api     "ollama"}]}}
            ctx (sut/prepare {:agent "main" :model "ollama/override:13b"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "override:13b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "resolves model alias from overrides map"
      (let [cfg    {:agents {:defaults {:model "ollama/default:7b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            models {"fast" {:model "llama3:8b" :provider "ollama" :contextWindow 8192}}
            ctx    (sut/prepare {:agent "main" :model "fast"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :models models})]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= 8192 (:context-window ctx))))

    (it "uses agent model when no override"
      (let [cfg    {:agents {:defaults {:model "ollama/default:7b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            agents {"coder" {:model "ollama/codellama:34b"}}
            ctx    (sut/prepare {:agent "coder"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "codellama:34b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "uses agent soul when available"
      (let [cfg    {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            agents {"soulful" {:soul "I am a custom soul." :model "ollama/qwen3-coder:30b"}}
            ctx    (sut/prepare {:agent "soulful"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "I am a custom soul." (:soul ctx))))

    (it "creates a new session by default"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should-contain "agent:main:cli:direct:" (:session-key ctx))))

    (it "resumes a specific session by key"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:testuser"
            _       (storage/create-session! test-dir key-str)
            ctx     (sut/prepare {:agent "main" :session key-str}
                                 {:cfg  cfg
                                  :sdir test-dir})]
        (should= key-str (:session-key ctx))))

    (it "resumes the most recent session with --resume"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:resumeuser"
            _       (storage/create-session! test-dir key-str)
            ctx     (sut/prepare {:agent "main" :resume true}
                                 {:cfg  cfg
                                  :sdir test-dir})]
        (should-contain "cli" (:session-key ctx))))

    (it "falls back to default context-window"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= 32768 (:context-window ctx))))

    (it "resolves agents.models alias for non-provider/model format"
      (let [cfg {:agents {:defaults {:model "fast"}
                          :models   {:fast {:model    "llama3:8b"
                                            :provider "ollama"}}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx))))))
