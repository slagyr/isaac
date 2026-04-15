(ns isaac.config.resolution-spec
  (:require
    [cheshire.core :as json]
    [isaac.config.resolution :as sut]
    [isaac.session.fs :as fs]
    [speclj.core :refer :all]))

(defn- write-json! [path data]
  (fs/write-file fs/*fs* path (json/generate-string data)))

(defn- write-file! [path content]
  (fs/write-file fs/*fs* path content))

(def test-root "/test/config")

(describe "Configuration Resolution"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  ;; region ----- Config File Resolution -----

  (describe "config file resolution"

    (it "loads openclaw.json when present"
      (write-json! (str test-root "/.openclaw/openclaw.json")
                   {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}})
      (let [config (sut/load-config {:home test-root})]
        (should= "ollama/qwen3-coder:30b" (get-in config [:agents :defaults :model]))))

    (it "falls back to isaac.json when no openclaw.json"
      (write-json! (str test-root "/.isaac/isaac.json")
                   {:agents {:defaults {:model "ollama/llama3:8b"}}})
      (let [config (sut/load-config {:home test-root})]
        (should= "ollama/llama3:8b" (get-in config [:agents :defaults :model]))))

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
          (should= ws-dir ws))))

    (it "falls back to isaac workspace directory"
      (let [ws-dir (str test-root "/.isaac/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac.")
        (let [ws (sut/resolve-workspace "main" {:home test-root})]
          (should= ws-dir ws))))

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
        (write-file! (str ws-dir "/dummy") "")
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

  ;; region ----- Agent Context Resolution -----

  (describe "resolve-agent-context"

    (it "resolves model and provider from provider/model reference in defaults"
      (let [cfg {:agents {:defaults {:model "grover/echo"}}
                 :models {:providers [{:name "grover" :baseUrl "http://fake"}]}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= "echo" (:model ctx))
        (should= "grover" (:provider ctx))
        (should= "http://fake" (get-in ctx [:provider-config :baseUrl]))))

    (it "returns nil model when no model is configured"
      (let [cfg {:agents {:defaults {}}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should-be-nil (:model ctx))))

    (it "includes soul from agent config"
      (let [cfg {:agents {:defaults {:model "ollama/qwen" :soul "Be helpful."}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= "Be helpful." (:soul ctx))))

    (it "uses agent-specific model over defaults"
      (let [cfg {:agents {:defaults {:model "ollama/default"}
                          :list     [{:id "coder" :model "ollama/code"}]}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "coder")]
        (should= "code" (:model ctx))))

    (it "resolves context window from provider config"
      (let [cfg {:agents {:defaults {:model "openai/gpt-5"}}
                 :models {:providers [{:name "openai" :baseUrl "https://api.openai.com/v1" :contextWindow 128000}]}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= 128000 (:context-window ctx))))

    (it "defaults context window to 32768 when not in provider config"
      (let [cfg {:agents {:defaults {:model "grover/echo"}}
                 :models {:providers [{:name "grover"}]}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= 32768 (:context-window ctx))))

    (it "resolves agents.models alias"
      (let [cfg {:agents {:defaults {:model "fast"}
                          :models   {:fast {:model "llama3:8b" :provider "ollama" :contextWindow 8192}}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= 8192 (:context-window ctx))))

    (it "reads soul from workspace SOUL.md when no explicit soul in agent config"
      (write-file! (str test-root "/.isaac/workspace-main/SOUL.md") "You are Dr. Prattlesworth.")
      (let [cfg {:agents {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "main" {:home test-root})]
        (should= "You are Dr. Prattlesworth." (:soul ctx))))

    (it "falls back to default soul string when no SOUL.md and no soul in agent config"
      (let [cfg {:agents {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "main" {:home test-root})]
        (should= "You are Isaac, a helpful AI assistant." (:soul ctx))))

    (it "agent config soul takes precedence over workspace SOUL.md"
      (write-file! (str test-root "/.isaac/workspace-main/SOUL.md") "You are Dr. Prattlesworth.")
      (let [cfg {:agents {:defaults {:model "ollama/qwen" :soul "I am the config soul."}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-agent-context cfg "main" {:home test-root})]
        (should= "I am the config soul." (:soul ctx)))))

  ;; endregion ^^^^^ Agent Context Resolution ^^^^^

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

  ;; region ----- Server Config -----

  (describe "server-config"

    (it "returns default port 6674 and host 0.0.0.0 when no config"
      (let [result (sut/server-config {})]
        (should= 6674 (:port result))
        (should= "0.0.0.0" (:host result))))

    (it "reads port from server.port"
      (should= 8080 (:port (sut/server-config {:server {:port 8080}}))))

    (it "reads host from server.host"
      (should= "127.0.0.1" (:host (sut/server-config {:server {:host "127.0.0.1"}}))))

    (it "aliases gateway.port to server.port"
      (should= 9000 (:port (sut/server-config {:gateway {:port 9000}}))))

    (it "aliases gateway.host to server.host"
      (should= "10.0.0.1" (:host (sut/server-config {:gateway {:host "10.0.0.1"}}))))

    (it "server.port takes precedence over gateway.port"
      (should= 8080 (:port (sut/server-config {:server {:port 8080} :gateway {:port 9000}}))))

    (it "defaults dev mode to false"
      (should= false (:dev (sut/server-config {}))))

    (it "reads dev mode from boolean config"
      (should= true (:dev (sut/server-config {:dev true}))))

    (it "reads dev mode from env-substituted string config"
      (should= true (:dev (sut/server-config {:dev "true"})))
      (should= false (:dev (sut/server-config {:dev "false"})))))

  ;; endregion ^^^^^ Server Config ^^^^^

  )
