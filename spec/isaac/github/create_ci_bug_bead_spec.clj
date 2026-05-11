(ns isaac.github.create-ci-bug-bead-spec
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]
    [clojure.string :as str]
    [speclj.core :refer :all]))

(defn- repo-root []
  (.getCanonicalPath (io/file ".")))

(defn- git-output [& args]
  (-> (apply sh/sh (concat ["git"] args [:dir (repo-root)]))
    :out
    str/trim))

(defn- create-temp-dir []
  (doto (.toFile (java.nio.file.Files/createTempDirectory "isaac-ci-bug-"
                            (make-array java.nio.file.attribute.FileAttribute 0)))
    (.deleteOnExit)))

(defn- run-script! [log-text]
  (let [dir      (create-temp-dir)
        log-file (doto (io/file dir "verify.log")
                   (.deleteOnExit))]
    (spit log-file log-text)
    (sh/sh "bash" ".github/scripts/create-ci-bug-bead.sh" (.getPath log-file)
      :dir (repo-root)
      :env (merge (into {} (System/getenv))
             {"BD_CREATE_DRY_RUN" "1"
              "GITHUB_ACTOR"      "micahmartin"
              "GITHUB_REF_NAME"   "main"
              "GITHUB_REPOSITORY" "slagyr/isaac"
               "GITHUB_RUN_ID"     "123"
               "GITHUB_SERVER_URL" "https://github.com"}))))

(describe "create-ci-bug-bead.sh"

  (it "uses the failure summary for the dry-run bead title"
    (let [result (run-script! "Finished in 12.3 seconds\n\u001b[31m538 examples, 1 failures, 1217 assertions\u001b[0m\n")
          output (str (:out result) (:err result))
          author (git-output "show" "-s" "--format=%an" "HEAD")]
      (should= 0 (:exit result))
      (should (re-find #"Title: CI red on [0-9a-f]{7}: 538 examples, 1 failures, 1217 assertions" output))
      (should (str/includes? output (str "Assignee: " author)))
      (should (str/includes? output "Summary:\n538 examples, 1 failures, 1217 assertions"))
      (should-not (str/includes? output "[31m"))))

  (it "falls back to the task error when no example summary is present"
    (let [result (run-script! "Error while executing task: Unknown option: --tags\n")
          output (str (:out result) (:err result))]
      (should= 0 (:exit result))
      (should (re-find #"Title: CI red on [0-9a-f]{7}: Error while executing task: Unknown option: --tags" output))
      (should (str/includes? output "Summary:\nError while executing task: Unknown option: --tags")))))
