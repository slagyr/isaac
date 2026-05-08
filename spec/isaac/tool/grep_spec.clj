(ns isaac.tool.grep-spec
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.grep :as sut]
    [isaac.tool.support :as support]
    [speclj.core :refer :all]))

(describe "Grep tool"
  (before (support/clean!))

  (around [it]
    (system/with-system {:state-dir support/test-dir}
      (it)))

  (it "returns matching lines with file and line prefixes"
    (support/write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
    (let [result (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src")})]
      (should-be-nil (:isError result))
      (should (str/includes? (:result result) "core.clj:1:"))
      (should (str/includes? (:result result) "(defn greet [name])"))
      (should (str/includes? (:result result) "core.clj:2:"))))

  (it "returns a clear no-matches result"
    (support/write-file! "src/core.clj" "(defn greet [name])")
    (let [result (sut/grep-tool {"pattern" "xyzzy" "path" (str support/test-dir "/src")})]
      (should-be-nil (:isError result))
      (should= "no matches" (:result result))))

  (it "limits matches using a glob filter"
    (support/write-file! "src/core.clj" "(defn greet [name])")
    (support/write-file! "src/notes.md" "defn is a Clojure macro")
    (let [result (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src") "glob" "*.clj"})]
      (should-be-nil (:isError result))
      (should (str/includes? (:result result) "core.clj"))
      (should-not (str/includes? (:result result) "notes.md"))))

  (it "truncates output at the requested head limit"
    (support/write-file! "big.txt" (str/join "\n" (map #(str "line " %) (range 1 11))))
    (let [result (sut/grep-tool {"pattern" "line" "path" (str support/test-dir "/big.txt") "head_limit" 5})]
      (should-be-nil (:isError result))
      (should (str/includes? (:result result) "line 1"))
      (should (str/includes? (:result result) "line 5"))
      (should-not (str/includes? (:result result) "line 6"))
      (should (str/includes? (:result result) "truncated"))))

  (it "rejects grep outside allowed directories"
    (let [session-key "main-session"]
      (helper/create-session! support/test-dir session-key {:crew "main" :cwd "/work/project"})
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["grep"]}}} :models {} :providers {}})]
                     (sut/grep-tool {"pattern"     "hunter"
                                     "path"        "/tmp/secret-stash"
                                     "session_key" session-key}))]
        (should (:isError result))
        (should (re-find #"path outside allowed directories" (:error result))))))

  (it "returns count mode output per file"
    (support/write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
    (support/write-file! "src/util.clj" "(defn only [name])")
    (let [result (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src") "output_mode" "count"})]
      (should-be-nil (:isError result))
      (should (str/includes? (:result result) "core.clj:2"))
      (should (str/includes? (:result result) "util.clj:1")))))
