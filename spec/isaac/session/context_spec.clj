(ns isaac.session.context-spec
  (:require
    [clojure.string :as str]
    [isaac.session.context :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-root "/test/session-context")

(describe "resolve-turn-context"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "cfg path (production)"

    (it "resolves model and provider from config"
      (let [cfg {:defaults  {:crew "main" :model "qwen"}
                 :crew      {"main" {}}
                 :models    {"qwen" {:model "qwen" :provider "ollama" :context-window 32768}}
                 :providers {"ollama" {:base-url "http://localhost:11434"}}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "qwen" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "resolves soul from workspace SOUL.md via cfg path"
      (fs/spit (str test-root "/.isaac/workspace-main/SOUL.md") "You are Dr. Prattlesworth.")
      (let [cfg {:defaults  {:crew "main" :model "qwen"}
                 :crew      {"main" {}}
                 :models    {"qwen" {:model "qwen" :provider "ollama" :context-window 32768}}
                 :providers {"ollama" {:base-url "http://localhost:11434"}}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "You are Dr. Prattlesworth." (:soul ctx))))

    (it "uses default soul when no SOUL.md via cfg path"
      (let [cfg {:defaults  {:crew "main" :model "qwen"}
                 :crew      {"main" {}}
                 :models    {"qwen" {:model "qwen" :provider "ollama" :context-window 32768}}
                 :providers {"ollama" {:base-url "http://localhost:11434"}}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root} "main")]
        (should= "You are Isaac, a helpful AI assistant." (:soul ctx))))

    (it "loads AGENTS.md from session cwd as boot context"
      (fs/spit (str test-root "/project/AGENTS.md") "## House Rules\nNo tabs.")
      (let [cfg {:defaults  {:crew "main" :model "qwen"}
                 :crew      {"main" {}}
                 :models    {"qwen" {:model "qwen" :provider "ollama" :context-window 32768}}
                 :providers {"ollama" {:base-url "http://localhost:11434"}}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root :cwd (str test-root "/project")} "main")]
        (should (str/includes? (:boot-files ctx) "House Rules"))
        (should (str/includes? (:soul ctx) "You are Isaac"))))

    (it "returns nil boot files when AGENTS.md is missing"
      (let [cfg {:defaults  {:crew "main" :model "qwen"}
                 :crew      {"main" {}}
                 :models    {"qwen" {:model "qwen" :provider "ollama" :context-window 32768}}
                 :providers {"ollama" {:base-url "http://localhost:11434"}}}
            ctx (sut/resolve-turn-context {:cfg cfg :home test-root :cwd (str test-root "/missing-project")} "main")]
        (should= nil (:boot-files ctx)))))

  (describe "injected maps path (test)"

    (it "resolves soul from injected crew"
      (let [crew-members {"main" {:name "main" :soul "Test soul." :model "grover"}}
            models       {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
            ctx          (sut/resolve-turn-context {:crew-members crew-members :models models} "main")]
        (should= "Test soul." (:soul ctx))
        (should= "echo" (:model ctx))
        (should= "grover" (:provider ctx))))

    (it "resolves soul from SOUL.md when no soul in injected crew"
      (fs/spit (str test-root "/.isaac/workspace-main/SOUL.md") "Workspace soul.")
      (let [crew-members {"main" {:name "main" :model "grover"}}
            models       {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
            ctx          (sut/resolve-turn-context {:crew-members crew-members :models models :home test-root} "main")]
        (should= "Workspace soul." (:soul ctx))))

    (it "uses default soul when no soul in injected crew and no SOUL.md"
      (let [crew-members {"main" {:name "main" :model "grover"}}
            models       {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
            ctx          (sut/resolve-turn-context {:crew-members crew-members :models models :home test-root} "main")]
        (should= "You are Isaac, a helpful AI assistant." (:soul ctx))))

    (it "loads AGENTS.md from cwd with injected crew maps"
      (fs/spit (str test-root "/workspace/AGENTS.md") "Use two spaces.")
      (let [crew-members {"main" {:name "main" :model "grover"}}
            models       {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
            ctx          (sut/resolve-turn-context {:crew-members crew-members :models models :home test-root :cwd (str test-root "/workspace")} "main")]
        (should= "Use two spaces." (:boot-files ctx))))))
