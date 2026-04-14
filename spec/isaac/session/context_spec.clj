(ns isaac.session.context-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.session.context :as sut]
    [speclj.core :refer :all]))

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- write-file! [path content]
  (io/make-parents path)
  (spit path content))

(def test-root "target/test-session-context")

(describe "resolve-turn-context"

  (before-all (clean-dir! test-root))
  (after (clean-dir! test-root))

  (describe "cfg path (production)"

    (it "resolves model and provider from config"
      (let [cfg {:agents {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "qwen" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "resolves soul from workspace SOUL.md via cfg path"
      (write-file! (str test-root "/.isaac/workspace-main/SOUL.md") "You are Dr. Prattlesworth.")
      (let [cfg {:agents {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "You are Dr. Prattlesworth." (:soul ctx))))

    (it "uses default soul when no SOUL.md via cfg path"
      (let [cfg {:agents {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "You are Isaac, a helpful AI assistant." (:soul ctx))))

    (it "loads AGENTS.md from session cwd as boot context"
      (write-file! (str test-root "/project/AGENTS.md") "## House Rules\nNo tabs.")
      (let [cfg {:crew   {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root :cwd (str test-root "/project")} "main")]
        (should (str/includes? (:boot-files ctx) "House Rules"))
        (should (str/includes? (:soul ctx) "You are Isaac"))))

    (it "returns nil boot files when AGENTS.md is missing"
      (let [cfg {:crew   {:defaults {:model "ollama/qwen"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root :cwd (str test-root "/missing-project")} "main")]
        (should= nil (:boot-files ctx)))))

  (describe "injected maps path (test)"

    (it "resolves soul from injected agent"
      (let [agents {"main" {:name "main" :soul "Test soul." :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}
            ctx    (sut/resolve-turn-context {:agents agents :models models} "main")]
        (should= "Test soul." (:soul ctx))
        (should= "echo" (:model ctx))
        (should= "grover" (:provider ctx))))

    (it "resolves soul from SOUL.md when no soul in injected agent"
      (write-file! (str test-root "/.isaac/workspace-main/SOUL.md") "Workspace soul.")
      (let [agents {"main" {:name "main" :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}
            ctx    (sut/resolve-turn-context {:agents agents :models models :home test-root} "main")]
        (should= "Workspace soul." (:soul ctx))))

    (it "uses default soul when no soul in injected agent and no SOUL.md"
      (let [agents {"main" {:name "main" :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}
            ctx    (sut/resolve-turn-context {:agents agents :models models :home test-root} "main")]
        (should= "You are Isaac, a helpful AI assistant." (:soul ctx))))

    (it "loads AGENTS.md from cwd with injected crew maps"
      (write-file! (str test-root "/workspace/AGENTS.md") "Use two spaces.")
      (let [agents {"main" {:name "main" :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}
            ctx    (sut/resolve-turn-context {:agents agents :models models :home test-root :cwd (str test-root "/workspace")} "main")]
        (should= "Use two spaces." (:boot-files ctx))))))
