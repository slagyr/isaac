(ns isaac.tool.grep-spec
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.spec-helper :as helper]
    [isaac.system :as system]
    [isaac.tool.grep :as sut]
    [isaac.tool.support :as support]
    [speclj.core :refer :all]))

(describe "Grep tool"
  (before (support/clean!))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (helper/with-memory-store
      (system/with-system {:state-dir support/test-dir}
        (example))))

  (describe "mocked rg"

    (it "returns matching lines with file and line prefixes"
      (let [captured (atom nil)
            result   (with-redefs [sut/available? (constantly true)
                                   sut/-run-rg   (fn [cmd]
                                                   (reset! captured cmd)
                                                   {:exit 0 :out "src/core.clj:1:(defn greet [name])\nsrc/core.clj:2:(defn shout [name])" :err ""})]
                       (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src")}))]
        (should-be-nil (:isError result))
        (should= ["rg" "--color=never" "--with-filename" "-n" "defn" (str support/test-dir "/src")] @captured)
        (should (str/includes? (:result result) "core.clj:1:"))
        (should (str/includes? (:result result) "(defn greet [name])"))
        (should (str/includes? (:result result) "core.clj:2:"))))

    (it "returns a clear no-matches result"
      (let [result (with-redefs [sut/available? (constantly true)
                                 sut/-run-rg   (fn [_] {:exit 1 :out "" :err ""})]
                     (sut/grep-tool {"pattern" "xyzzy" "path" (str support/test-dir "/src")}))]
        (should-be-nil (:isError result))
        (should= "no matches" (:result result))))

    (it "limits matches using a glob filter"
      (let [captured (atom nil)
            result   (with-redefs [sut/available? (constantly true)
                                   sut/-run-rg   (fn [cmd]
                                                   (reset! captured cmd)
                                                   {:exit 0 :out "src/core.clj:1:(defn greet [name])" :err ""})]
                       (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src") "glob" "*.clj"}))]
        (should-be-nil (:isError result))
        (should= ["rg" "--color=never" "--with-filename" "-n" "-g" "*.clj" "defn" (str support/test-dir "/src")] @captured)
        (should (str/includes? (:result result) "core.clj"))
        (should-not (str/includes? (:result result) "notes.md"))))

    (it "truncates output at the requested head limit"
      (let [output (str/join "\n" (map #(str "big.txt:" % ":line " %) (range 1 11)))
            result (with-redefs [sut/available? (constantly true)
                                 sut/-run-rg   (fn [_] {:exit 0 :out output :err ""})]
                     (sut/grep-tool {"pattern" "line" "path" (str support/test-dir "/big.txt") "head_limit" 5}))]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "line 1"))
        (should (str/includes? (:result result) "line 5"))
        (should-not (str/includes? (:result result) "line 6"))
        (should (str/includes? (:result result) "truncated"))))

    (it "returns files_with_matches mode output as sorted paths"
      (let [captured (atom nil)
            result   (with-redefs [sut/available? (constantly true)
                                   sut/-run-rg   (fn [cmd]
                                                   (reset! captured cmd)
                                                   {:exit 0 :out "src/core.clj\nsrc/util.clj" :err ""})]
                       (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src") "output_mode" "files_with_matches"}))]
        (should-be-nil (:isError result))
        (should= ["rg" "--color=never" "-l" "--sort" "path" "defn" (str support/test-dir "/src")] @captured)
        (should (str/includes? (:result result) "core.clj"))
        (should (str/includes? (:result result) "util.clj"))))
    (it "returns count mode output per file"
      (let [captured (atom nil)
            result   (with-redefs [sut/available? (constantly true)
                                   sut/-run-rg   (fn [cmd]
                                                   (reset! captured cmd)
                                                   {:exit 0 :out "src/core.clj:2\nsrc/util.clj:1" :err ""})]
                        (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src") "output_mode" "count"}))]
        (should-be-nil (:isError result))
        (should= ["rg" "--color=never" "-c" "--sort" "path" "defn" (str support/test-dir "/src")] @captured)
        (should (str/includes? (:result result) "core.clj:2"))
        (should (str/includes? (:result result) "util.clj:1"))))

    (it "returns an error when rg is unavailable"
      (let [result (with-redefs [sut/available? (constantly false)]
                     (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src")}))]
        (should (:isError result))
        (should= "rg not found on PATH" (:error result)))))

  (it "rejects grep outside allowed directories"
    (let [session-key "main-session"]
      (helper/create-session! support/test-dir session-key {:crew "main" :cwd "/work/project"})
      (let [result (with-redefs [config/load-config (fn [& _] {:defaults {} :crew {"main" {:tools {:allow ["grep"]}}} :models {} :providers {}})]
                     (sut/grep-tool {"pattern"     "hunter"
                                     "path"        "/tmp/secret-stash"
                                     "session_key" session-key}))]
        (should (:isError result))
        (should (re-find #"path outside allowed directories" (:error result))))))

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (describe "path resolution against session cwd"
    (with session-key "grep-res-session")
    (with cwd (str support/test-dir "/crew/main/workspace"))

    (before
      (.mkdirs (io/file @cwd))
      (helper/create-session! support/test-dir @session-key {:crew "main" :cwd @cwd}))

    (it "resolves '.' to session cwd"
      (let [captured (atom nil)]
        (with-redefs [sut/available? (constantly true)
                      sut/-run-rg   (fn [cmd] (reset! captured cmd) {:exit 1 :out "" :err ""})
                      config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
          (sut/grep-tool {"pattern" "needle" "path" "." "session_key" @session-key}))
        (should= @cwd (last @captured))))

    (it "resolves an empty path to session cwd"
      (let [captured (atom nil)]
        (with-redefs [sut/available? (constantly true)
                      sut/-run-rg   (fn [cmd] (reset! captured cmd) {:exit 1 :out "" :err ""})
                      config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
          (sut/grep-tool {"pattern" "needle" "path" "" "session_key" @session-key}))
        (should= @cwd (last @captured))))

    (it "resolves a relative path against session cwd"
      (let [captured (atom nil)
            subdir   (str @cwd "/src")]
        (.mkdirs (io/file subdir))
        (with-redefs [sut/available? (constantly true)
                      sut/-run-rg   (fn [cmd] (reset! captured cmd) {:exit 1 :out "" :err ""})
                      config/load-config (fn [& _] {:defaults {} :crew {} :models {} :providers {}})]
          (sut/grep-tool {"pattern" "needle" "path" "src" "session_key" @session-key}))
        (should= subdir (last @captured)))))

  (it "runs the real rg binary when available"
    (when (sut/available?)
      (support/write-file! "src/core.clj" "(defn greet [name])\n(defn shout [name])")
      (let [result (sut/grep-tool {"pattern" "defn" "path" (str support/test-dir "/src")})]
        (should-be-nil (:isError result))
        (should (str/includes? (:result result) "core.clj:1:"))
        (should (str/includes? (:result result) "core.clj:2:"))))))
