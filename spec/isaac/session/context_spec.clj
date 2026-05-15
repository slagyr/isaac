(ns isaac.session.context-spec
  (:require
    [isaac.fs :as fs]
    [isaac.session.context :as sut]
    [speclj.core :refer [around describe it should should-be-nil]]))

(def test-root "/test/session-context")

(describe "read-boot-files"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "reads AGENTS.md from the cwd"
    (fs/spit (str test-root "/project/AGENTS.md") "## House Rules\nNo tabs.")
    (let [boot-files (sut/read-boot-files (str test-root "/project"))]
      (should (.contains boot-files "House Rules"))))

  (it "returns nil when AGENTS.md is missing"
    (should-be-nil (sut/read-boot-files (str test-root "/missing-project")))))
