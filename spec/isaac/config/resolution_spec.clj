(ns isaac.config.resolution-spec
  (:require
    [isaac.config.resolution :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-root "/test/config")

(defn- write-config! [path data]
  (fs/spit path (pr-str data)))

(defn- write-file! [path content]
  (fs/spit path content))

(describe "Configuration Resolution"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "load-config"

    (it "loads the new config directory layout"
      (write-config! (str test-root "/.isaac/config/isaac.edn") {:defaults {:crew :main :model :llama}
                                                                   :crew {:main {:soul "You are Isaac."}}})
      (let [config (sut/load-config {:home test-root})]
        (should= "main" (get-in config [:defaults :crew]))
        (should= "You are Isaac." (get-in config [:crew "main" :soul]))))

    (it "returns built-in defaults when no config files exist"
      (let [config (sut/load-config {:home test-root})]
        (should= "main" (get-in config [:defaults :crew]))
        (should= "llama3.3:1b" (get-in config [:models "llama" :model])))))

  (describe "workspace resolution"

    (it "resolves openclaw workspace directory"
      (let [ws-dir (str test-root "/.openclaw/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac.")
        (should= ws-dir (sut/resolve-workspace "main" {:home test-root}))))

    (it "reads workspace files"
      (let [ws-dir (str test-root "/.isaac/workspace-main")]
        (write-file! (str ws-dir "/SOUL.md") "You are Isaac, a helpful assistant.")
        (should= "You are Isaac, a helpful assistant."
                 (sut/read-workspace-file "main" "SOUL.md" {:home test-root})))) )

  (describe "resolve-agent-context"

    (it "resolves model and provider from the new map-by-id shape"
      (let [cfg {:defaults  {:crew "main" :model "llama"}
                 :crew      {"main" {:soul "Be helpful." :model "grover"}}
                 :models    {"grover" {:model "echo" :provider "grover" :context-window 8192}}
                 :providers {"grover" {:base-url "http://fake"}}}
            ctx (sut/resolve-agent-context cfg "main")]
        (should= "Be helpful." (:soul ctx))
        (should= "echo" (:model ctx))
        (should= "grover" (:provider ctx))
        (should= 8192 (:context-window ctx))))

    (it "reads soul from workspace SOUL.md when the crew config omits it"
      (write-file! (str test-root "/.isaac/workspace-main/SOUL.md") "Workspace soul.")
      (let [cfg {:defaults  {:crew "main" :model "grover"}
                 :crew      {"main" {:model "grover"}}
                 :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                 :providers {"grover" {:base-url "http://fake"}}}
            ctx (sut/resolve-agent-context cfg "main" {:home test-root})]
        (should= "Workspace soul." (:soul ctx)))))

  (describe "env variable substitution"

    (it "substitutes ${VAR} in string values"
      (write-config! (str test-root "/.isaac/config/providers/anthropic.edn") {:api-key "${TEST_ISAAC_API_KEY}"})
      (with-redefs [isaac.config.loader/env (fn [name] (when (= "TEST_ISAAC_API_KEY" name) "sk-test-123"))]
        (let [config (sut/load-config {:home test-root})]
          (should= "sk-test-123" (get-in config [:providers "anthropic" :api-key]))))))

  (describe "server-config"

    (it "returns default port 6674 and host 0.0.0.0 when no config"
      (let [result (sut/server-config {})]
        (should= 6674 (:port result))
        (should= "0.0.0.0" (:host result))))

    (it "aliases gateway.port to server.port"
      (should= 9000 (:port (sut/server-config {:gateway {:port 9000}}))))))
