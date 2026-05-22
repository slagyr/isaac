(ns isaac.session.context-spec
  (:require
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [isaac.server.routes]
    [isaac.session.store :as store]
    [isaac.spec-helper :as helper]
    [isaac.session.context :as sut]
    [isaac.system :as system]
    [speclj.core :refer [around describe it should should-be-nil should=]]))

(def test-root "/test/session-context")
(def crew-name marigold/captain)
(def crew-soul (:soul (marigold/crew-cfg crew-name)))

(describe "read-boot-files"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (system/with-system {:fs (fs/mem-fs)}
      (example)))

  (it "reads AGENTS.md from the cwd"
    (fs/spit (system/get :fs) (str test-root "/project/AGENTS.md") "## House Rules\nNo tabs.")
    (let [boot-files (sut/read-boot-files (str test-root "/project"))]
      (should (.contains boot-files "House Rules"))))

  (it "returns nil when AGENTS.md is missing"
    (should-be-nil (sut/read-boot-files (str test-root "/missing-project")))))

  (it "reads AGENTS.md from the installed runtime fs without binding fs/*fs*"
    (let [mem (fs/mem-fs)]
      (fs/spit mem (str test-root "/project-runtime/AGENTS.md") "## Runtime Rules\nNo globals.")
      (system/with-system {:fs mem}
        (let [boot-files (sut/read-boot-files (str test-root "/project-runtime"))]
          (should (.contains boot-files "Runtime Rules"))))))

(describe "behavior funnel"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (helper/with-memory-store
      (system/with-nested-system {:state-dir test-root :fs (fs/mem-fs)}
        (example))))

  (it "resolves locked and cascade fields for an existing session"
    (config/set-snapshot! {:defaults  {:crew crew-name :model "spark" :effort 5 :history-retention :prune}
                           :crew      {crew-name {:model "spark" :soul crew-soul :context-mode :reset :compaction {:threshold 0.7}}}
                           :models    {"spark" {:model "echo" :provider "grover" :context-window 1000 :effort 6 :compaction {:threshold 0.6}}}
                           :providers {"grover" {:api "grover" :effort 7 :compaction {:threshold 0.5}}}})
    (helper/create-session! test-root "s" {:crew crew-name :cwd "/tmp/locked" :history-retention :retain})
    (let [behavior (sut/resolve-behavior "s")]
      (should= crew-name (:crew behavior))
      (should= "/tmp/locked" (:cwd behavior))
      (should= :retain (:history-retention behavior))
      (should= :reset (:context-mode behavior))
      (should= 6 (:effort behavior))
      (should= {:async? false :strategy :rubberband :head 0.3 :threshold 0.7} (:compaction behavior)))
    (config/set-snapshot! nil))

  (it "creates a session with resolved locked defaults and explicit overrides"
    (config/set-snapshot! {:defaults  {:crew crew-name :model "spark" :history-retention :prune}
                           :crew      {crew-name {:model "spark" :soul crew-soul}}
                           :models    {"spark" {:model "echo" :provider "grover" :context-window 1000}}
                           :providers {"grover" {:api "grover"}}})
    (sut/create-with-resolved-behavior! "s" {:effort 9})
    (let [session (helper/get-session test-root "s")]
      (should= crew-name (:crew session))
      (should= (str "/test/session-context/.isaac/crew/" crew-name) (:cwd session))
      (should= :prune (:history-retention session))
      (should= 9 (:effort session)))
    (config/set-snapshot! nil))

  (it "creates a session in an explicit session store"
    (config/set-snapshot! {:defaults  {:crew crew-name :model "spark" :history-retention :prune}
                           :crew      {crew-name {:model "spark" :soul crew-soul}}
                           :models    {"spark" {:model "echo" :provider "grover" :context-window 1000}}
                           :providers {"grover" {:api "grover"}}})
    (let [explicit-store (store/create nil :memory)]
      (sut/create-with-resolved-behavior! "s" {:effort 9 :session-store explicit-store})
      (should-be-nil (helper/get-session test-root "s"))
      (let [session (store/get-session explicit-store "s")]
        (should= crew-name (:crew session))
        (should= (str "/test/session-context/.isaac/crew/" crew-name) (:cwd session))
        (should= :prune (:history-retention session))
        (should= 9 (:effort session))))
    (config/set-snapshot! nil)))
