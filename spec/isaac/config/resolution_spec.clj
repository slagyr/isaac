(ns isaac.config.resolution-spec
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [isaac.config.resolution :as sut]
    [speclj.core :refer :all]))

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- write-json! [path data]
  (io/make-parents path)
  (spit path (json/generate-string data)))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(def test-root "target/test-config")

(describe "Configuration Resolution"

  (before-all (clean-dir! test-root))
  (after (clean-dir! test-root))

  ;; region ----- Config File Resolution -----

  (describe "config file resolution"

    (it "loads openclaw.json when present"
      (let [oc-dir (str test-root "/.openclaw")]
        (write-json! (str oc-dir "/openclaw.json")
                     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}})
        (let [config (sut/load-config {:home test-root})]
          (should= "ollama/qwen3-coder:30b" (get-in config [:agents :defaults :model])))))

    (it "falls back to isaac.json when no openclaw.json"
      (let [isaac-dir (str test-root "/.isaac")]
        (write-json! (str isaac-dir "/isaac.json")
                     {:agents {:defaults {:model "ollama/llama3:8b"}}})
        (let [config (sut/load-config {:home test-root})]
          (should= "ollama/llama3:8b" (get-in config [:agents :defaults :model])))))

    (it "returns defaults when no config files exist"
      (let [config (sut/load-config {:home test-root})]
        (should-not-be-nil config)
        (should (map? config))))

    (it "prefers openclaw.json over isaac.json"
      (write-json! (str test-root "/.openclaw/openclaw.json")
                   {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}})
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "ollama/llama3:8b"}}})
      (let [config (sut/load-config {:home test-root})]
        (should= "ollama/qwen3-coder:30b" (get-in config [:agents :defaults :model])))))

  ;; endregion ^^^^^ Config File Resolution ^^^^^

  ;; region ----- Workspace Resolution -----

  (describe "workspace resolution"

    (it "resolves openclaw workspace directory"
      (let [ws-dir (str test-root "/.openclaw/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac.")
        (let [ws (sut/resolve-workspace "main" {:home test-root})]
          (should= (str ws-dir) ws))))

    (it "falls back to isaac workspace directory"
      (let [ws-dir (str test-root "/.isaac/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac.")
        (let [ws (sut/resolve-workspace "main" {:home test-root})]
          (should= (str ws-dir) ws))))

    (it "returns nil when no workspace exists"
      (should-be-nil (sut/resolve-workspace "unknown" {:home test-root}))))

  ;; endregion ^^^^^ Workspace Resolution ^^^^^

  ;; region ----- Workspace Files -----

  (describe "workspace files"

    (it "reads SOUL.md"
      (let [ws-dir (str test-root "/.isaac/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac, a helpful assistant.")
        (should= "You are Isaac, a helpful assistant."
                 (sut/read-workspace-file "main" "SOUL.md" {:home test-root}))))

    (it "returns nil for missing workspace file"
      (let [ws-dir (str test-root "/.isaac/workspace-main")]
        (io/make-parents (str ws-dir "/dummy"))
        (should-be-nil (sut/read-workspace-file "main" "TOOLS.md" {:home test-root})))))

  ;; endregion ^^^^^ Workspace Files ^^^^^

  ;; region ----- Agent Resolution -----

  (describe "agent resolution"

    (it "resolves agent from config list"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "ollama/qwen3-coder:30b"}
                             :list     [{:id "main" :model "ollama/llama3:8b"}
                                        {:id "researcher"}]}})
      (let [config (sut/load-config {:home test-root})
            agent  (sut/resolve-agent config "main")]
        (should= "ollama/llama3:8b" (:model agent))))

    (it "uses defaults when agent has no override"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "ollama/qwen3-coder:30b"}
                             :list     [{:id "researcher"}]}})
      (let [config (sut/load-config {:home test-root})
            agent  (sut/resolve-agent config "researcher")]
        (should= "ollama/qwen3-coder:30b" (:model agent))))

    (it "returns defaults for unknown agent"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}})
      (let [config (sut/load-config {:home test-root})
            agent  (sut/resolve-agent config "unknown")]
        (should= "ollama/qwen3-coder:30b" (:model agent)))))

  ;; endregion ^^^^^ Agent Resolution ^^^^^

  ;; region ----- Model Resolution -----

  (describe "model resolution"

    (it "parses provider/model format"
      (let [result (sut/parse-model-ref "ollama/qwen3-coder:30b")]
        (should= "ollama" (:provider result))
        (should= "qwen3-coder:30b" (:model result))))

    (it "resolves provider config"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:models {:providers [{:name    "ollama"
                                          :baseUrl "http://localhost:11434"
                                          :api     "ollama"}]}})
      (let [config   (sut/load-config {:home test-root})
            provider (sut/resolve-provider config "ollama")]
        (should= "http://localhost:11434" (:baseUrl provider)))))

  ;; endregion ^^^^^ Model Resolution ^^^^^

  ;; region ----- Env Substitution -----

  (describe "env variable substitution"

    (it "substitutes ${VAR} in string values"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:models {:providers [{:name   "anthropic"
                                          :apiKey "${TEST_ISAAC_API_KEY}"}]}})
      (with-redefs [sut/env (fn [name] (when (= "TEST_ISAAC_API_KEY" name) "sk-test-123"))]
        (let [config   (sut/load-config {:home test-root})
              provider (sut/resolve-provider config "anthropic")]
          (should= "sk-test-123" (:apiKey provider)))))

    (it "replaces missing env var with empty string"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:models {:providers [{:name   "anthropic"
                                          :apiKey "${NONEXISTENT_VAR_12345}"}]}})
      (let [config   (sut/load-config {:home test-root})
            provider (sut/resolve-provider config "anthropic")]
        (should= "" (:apiKey provider))))

    (it "leaves non-env strings untouched"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:models {:providers [{:name    "ollama"
                                          :baseUrl "http://localhost:11434"}]}})
      (let [config   (sut/load-config {:home test-root})
            provider (sut/resolve-provider config "ollama")]
        (should= "http://localhost:11434" (:baseUrl provider))))

    (it "substitutes in nested structures"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "${TEST_ISAAC_MODEL}"}}})
      (with-redefs [sut/env (fn [name] (when (= "TEST_ISAAC_MODEL" name) "ollama/llama3:8b"))]
        (let [config (sut/load-config {:home test-root})]
          (should= "ollama/llama3:8b" (get-in config [:agents :defaults :model]))))))

  ;; endregion ^^^^^ Env Substitution ^^^^^

  )
