(ns isaac.bean-gate-spec
  (:require
    [babashka.fs :as fs]
    [babashka.process :as process]
    [clojure.string :as str]
    [isaac.bean-gate :as sut]
    [speclj.core :refer :all]))

(def ^:private fx-root (atom nil))

(defn- sh
  ([dir & args]
   (let [r (apply process/shell {:dir (str dir) :out :string :err :string :continue true} args)]
     (when-not (zero? (:exit r))
       (throw (ex-info (str "cmd failed " args "\n" (:err r) (:out r))
                       {:exit (:exit r) :out (:out r) :err (:err r)})))
     (str/trim-newline (:out r)))))

(defn- git [dir & args]
  (apply sh dir (into ["git"] args)))

(defn- init-repo [dir]
  (fs/create-dirs dir)
  (git dir "init" "-b" "main")
  (git dir "config" "user.email" "dev@example.com")
  (git dir "config" "user.name" "Dev")
  (git dir "config" "commit.gpgsign" "false"))

(defn- spit-file [dir rel content]
  (let [f (fs/file dir rel)]
    (fs/create-dirs (fs/parent f))
    (spit f content)
    f))

(defn- commit!
  ([dir msg] (commit! dir msg nil))
  ([dir msg trailers]
   (git dir "add" "-A")
   (let [args (cond-> ["commit" "-m" msg]
                trailers (concat (mapcat (fn [[k v]] ["--trailer" (str k ": " v)]) trailers)))]
     (apply git dir args))))

(def sample-feature
  (str "Feature: cans\n"
       "  Count the cans.\n"
       "\n"
       "  Background:\n"
       "    Given an empty bin\n"
       "\n"
       "  @wip\n"
       "  Scenario: count one can\n"
       "    When I add a can\n"
       "    Then the count is 1\n"
       "\n"
       "  Scenario: already shipped\n"
       "    Then the count is 0\n"))

(defn- setup-fx []
  (let [root     (fs/absolutize (fs/path "target" (str "bg-" (System/nanoTime))))
        isaac    (fs/path root "isaac")
        bare     (fs/path root "mod.git")
        module   (fs/path root "mod")
        bean-rel ".beans/isaac-cans--count-the-cans.md"
        bean-body (str "---\n# isaac-cans\ntitle: count the cans\nstatus: in-progress\n---\n\n"
                       "## Acceptance\n\n"
                       "bb features features/cans.feature\n")]
    (fs/create-dirs root)
    (init-repo isaac)
    (spit-file isaac bean-rel bean-body)
    (commit! isaac "add bean")
    (sh root "git" "init" "--bare" "-b" "main" (str bare))
    (sh root "git" "clone" (str bare) (str module))
    (git module "config" "user.email" "dev@example.com")
    (git module "config" "user.name" "Dev")
    (git module "config" "commit.gpgsign" "false")
    (spit-file module "features/cans.feature" sample-feature)
    (commit! module "plant @wip")
    (git module "push" "-u" "origin" "HEAD:main")
    (reset! fx-root {:root root :isaac isaac :module module :bare bare :bean-rel bean-rel})
    @fx-root))

(defn- bean-text [fx]
  (slurp (str (fs/file (:isaac fx) (:bean-rel fx)))))

(defn- baseline!
  ([fx] (baseline! fx ["mod:features/cans.feature"]))
  ([fx targets]
   (sut/run (concat ["baseline" "isaac-cans"]
                    targets
                    ["--dir" (str "mod=" (:module fx))]
                    ["--isaac" (str (:isaac fx))]))))

(defn- verify!
  ([fx] (verify! fx []))
  ([fx extra]
   (sut/run (concat ["verify" "isaac-cans"
                     "--dir" (str "mod=" (:module fx))
                     "--isaac" (str (:isaac fx))]
                    extra))))

(describe "bean-gate"

  (around [it]
    (let [fx (setup-fx)]
      (try
        (it)
        (finally
          (try (fs/delete-tree (:root fx)) (catch Exception _))))))

  (it "prints usage for --help and exits 0"
    (let [{:keys [exit message]} (sut/run ["--help"])]
      (should= 0 exit)
      (should (str/includes? message "baseline"))
      (should (str/includes? message "verify"))))

  (it "verify without a baseline exits 2"
    (let [fx @fx-root
          {:keys [exit message]} (verify! fx)]
      (should= 2 exit)
      (should (str/includes? message "no feature-baseline"))))

  (it "baseline appends one baseline and one blob line per file"
    (let [fx     @fx-root
          result (baseline! fx)
          text   (bean-text fx)
          sha    (git (:module fx) "rev-parse" "origin/main")
          blob   (git (:module fx) "rev-parse" (str sha ":features/cans.feature"))]
      (should= 0 (:exit result))
      (should (str/includes? text (str "feature-baseline: mod " sha)))
      (should (str/includes? text (str "feature-blob: mod features/cans.feature " blob)))
      (should (str/includes? (:message result) "feature-baseline:"))))

  (it "baseline line-list form records the scenario lines"
    (let [fx     @fx-root
          result (baseline! fx ["mod:features/cans.feature:8"])
          text   (bean-text fx)
          sha    (git (:module fx) "rev-parse" "origin/main")
          blob   (git (:module fx) "rev-parse" (str sha ":features/cans.feature"))]
      (should= 0 (:exit result))
      (should (str/includes? text (str "feature-blob: mod features/cans.feature " blob " 8")))))

  (it "baseline refuses a path with no @wip"
    (let [fx @fx-root]
      (spit-file (:module fx) "features/cans.feature"
                 "Feature: cans\n\n  Scenario: already shipped\n    Then the count is 0\n")
      (commit! (:module fx) "drop wip")
      (git (:module fx) "push" "origin" "HEAD:main")
      (let [{:keys [exit message]} (baseline! fx)]
        (should-not= 0 exit)
        (should (str/includes? message "features/cans.feature")))))

  (it "baseline refuses a missing file"
    (let [fx @fx-root
          {:keys [exit message]} (baseline! fx ["mod:features/missing.feature"])]
      (should-not= 0 exit)
      (should (str/includes? message "features/missing.feature"))))

  (it "baseline --dir override uses the named checkout"
    (let [fx     @fx-root
          result (baseline! fx)]
      (should= 0 (:exit result))
      (should (str/includes? (bean-text fx) "feature-baseline: mod"))))

  (context "after a planner baseline"

    (around [it]
      (let [fx @fx-root]
        (baseline! fx)
        (commit! (:isaac fx) "planner baseline")
        (it)))

    (it "passes on a pure @wip removal"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str/replace sample-feature "  @wip\n" ""))
        (commit! (:module fx) "drop @wip")
        (let [{:keys [exit]} (verify! fx)]
          (should= 0 exit))))

    (it "passes when another bean later adds a scenario to the same file"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str sample-feature
                        "\n  Scenario: extra can\n    Then the count is 2\n"))
        (commit! (:module fx) "other bean added a scenario")
        (git (:module fx) "push" "origin" "HEAD:main")
        (spit-file (:module fx) "features/cans.feature"
                   (str (str/replace sample-feature "  @wip\n" "")
                        "\n  Scenario: extra can\n    Then the count is 2\n"))
        (commit! (:module fx) "drop @wip keep extra")
        (let [{:keys [exit]} (verify! fx)]
          (should= 0 exit))))

    (it "passes when another bean's @wip scenario in the file is still @wip"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str sample-feature
                        "\n  @wip\n  Scenario: later work\n    Then pending\n"))
        (commit! (:module fx) "other bean still wip")
        (git (:module fx) "push" "origin" "HEAD:main")
        (spit-file (:module fx) "features/cans.feature"
                   (str (str/replace sample-feature "  @wip\n" "")
                        "\n  @wip\n  Scenario: later work\n    Then pending\n"))
        (commit! (:module fx) "drop own @wip")
        (let [{:keys [exit]} (verify! fx)]
          (should= 0 exit))))

    (it "fails on a reworded step"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (-> sample-feature
                       (str/replace "  @wip\n" "")
                       (str/replace "Then the count is 1" "Then the count is one")))
        (commit! (:module fx) "reword")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (str/includes? message "count is 1")))))

    (it "fails on a deleted baselined scenario"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str "Feature: cans\n"
                        "  Count the cans.\n"
                        "\n"
                        "  Background:\n"
                        "    Given an empty bin\n"
                        "\n"
                        "  Scenario: already shipped\n"
                        "    Then the count is 0\n"))
        (commit! (:module fx) "delete scenario")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (str/includes? (str/lower-case message) "count one can")))))

    (it "fails on an added @slow"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str/replace sample-feature "  @wip\n" "  @slow\n"))
        (commit! (:module fx) "retag slow")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (re-find #"(?i)slow|wip|tag|worker" message)))))

    (it "fails when a step is inserted into a baselined scenario"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (-> sample-feature
                       (str/replace "  @wip\n" "")
                       (str/replace "    When I add a can\n"
                                    "    When I add a can\n    And I shake the bin\n")))
        (commit! (:module fx) "insert step")
        (let [{:keys [exit]} (verify! fx)]
          (should= 1 exit))))

    (it "fails when the bean's scenario is still @wip"
      (let [fx @fx-root
            {:keys [exit message]} (verify! fx)]
        (should= 1 exit)
        (should (re-find #"(?i)wip" message))))

    (it "fails on a non-@wip edit to an unrelated .feature in the worker diff"
      (let [fx @fx-root]
        (spit-file (:module fx) "features/cans.feature"
                   (str/replace sample-feature "  @wip\n" ""))
        (spit-file (:module fx) "features/other.feature"
                   "Feature: other\n  Scenario: extra\n    Then yes\n")
        (commit! (:module fx) "drop wip and add other feature")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (str/includes? message "features/other.feature")))))

    (it "fails when a contract line is edited or removed in a later commit"
      (let [fx   @fx-root
            path (fs/file (:isaac fx) (:bean-rel fx))
            text (slurp path)
            sha  (git (:module fx) "rev-parse" "origin/main")]
        (spit path (str/replace text (re-pattern (str "feature-baseline: mod " sha))
                                "feature-baseline: mod deadbeef"))
        (commit! (:isaac fx) "edit contract")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (re-find #"(?i)contract" message)))))

    (it "passes when a contract line is appended"
      (let [fx   @fx-root
            path (fs/file (:isaac fx) (:bean-rel fx))]
        (spit path (str (slurp path) "\nfeature-note: extra\n"))
        (commit! (:isaac fx) "append note")
        (spit-file (:module fx) "features/cans.feature"
                   (str/replace sample-feature "  @wip\n" ""))
        (commit! (:module fx) "drop @wip")
        (let [{:keys [exit]} (verify! fx)]
          (should= 0 exit))))

    (it "fails when a baseline line was introduced by an Isaac-Session: isaac-work-1 commit"
      (let [fx   @fx-root
            path (fs/file (:isaac fx) (:bean-rel fx))
            text (slurp path)]
        (git (:isaac fx) "reset" "--hard" "HEAD~1")
        (spit path text)
        (commit! (:isaac fx) "worker baseline" {"Isaac-Session" "isaac-work-1"})
        (spit-file (:module fx) "features/cans.feature"
                   (str/replace sample-feature "  @wip\n" ""))
        (commit! (:module fx) "drop @wip")
        (let [{:keys [exit message]} (verify! fx)]
          (should= 1 exit)
          (should (re-find #"isaac-work" message))))))

  (it "re-baseline: the newer blob is in force"
    (let [fx @fx-root]
      (baseline! fx)
      (commit! (:isaac fx) "first baseline")
      (spit-file (:module fx) "features/cans.feature"
                 (str sample-feature
                      "\n  @wip\n  Scenario: count two cans\n    Then the count is 2\n"))
      (commit! (:module fx) "add second wip")
      (git (:module fx) "push" "origin" "HEAD:main")
      (baseline! fx)
      (commit! (:isaac fx) "re-baseline")
      (spit-file (:module fx) "features/cans.feature"
                 (-> (str sample-feature
                          "\n  Scenario: count two cans\n    Then the count is 2\n")
                     (str/replace "  @wip\n" "")))
      (commit! (:module fx) "drop both wips")
      (let [{:keys [exit]} (verify! fx)]
        (should= 0 exit))))

  (it "main-sha mode checks the squash commit's own diff"
    (let [fx  @fx-root
          _   (baseline! fx)
          _   (commit! (:isaac fx) "planner baseline")
          _   (spit-file (:module fx) "features/cans.feature"
                         (str/replace sample-feature "  @wip\n" ""))
          _   (commit! (:module fx) "drop @wip")
          sha (git (:module fx) "rev-parse" "HEAD")
          path (fs/file (:isaac fx) (:bean-rel fx))]
      (spit path (str (slurp path) "\nmain-sha: mod " sha "\n"))
      (commit! (:isaac fx) "record main-sha")
      (let [{:keys [exit]} (verify! fx)]
        (should= 0 exit))))
  )
