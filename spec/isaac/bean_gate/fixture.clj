(ns isaac.bean-gate.fixture
  "Throwaway git worlds under target/: an isaac clone with .beans/ and a module
   clone (isaac-marigold) with a bare origin, side by side."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def repo "isaac-marigold")
(def feature "features/longwave/relay.feature")

(def relay-feature
  "Feature: Longwave relay
  Cordelia relays skybeam messages over longwave.

  Background:
    Given the longwave relay is tuned to \"skybeam\"

  @wip
  Scenario: a relayed message reaches the logbook
    When Cordelia relays \"tide report\"
    Then the logbook contains \"tide report\"

  Scenario: an empty message is refused
    When Cordelia relays \"\"
    Then the relay reports \"nothing to send\"
")

(def relayed-line 8)

(defn sh [dir & args]
  (let [{:keys [exit out err]} (apply p/shell {:dir (str dir) :out :string :err :string :continue true} args)]
    (when-not (zero? exit) (throw (ex-info (str (str/join " " args) ": " err) {:dir dir})))
    (str/trim out)))

(defn- init! [dir]
  (sh dir "git" "config" "user.name" "Marigold Planner")
  (sh dir "git" "config" "user.email" "planner@marigold.test")
  (sh dir "git" "config" "commit.gpgsign" "false")
  (sh dir "git" "config" "core.hooksPath" "/dev/null"))

(defn world!
  "A fresh world. Returns {:root <isaac clone> :module <module clone> :origin <bare>}."
  []
  (let [base   (fs/create-dirs (fs/path "target" "bean-gate-spec" (str (random-uuid))))
        base   (str (fs/absolutize base))
        root   (str (fs/path base "isaac"))
        origin (str (fs/path base (str repo ".git")))
        module (str (fs/path base repo))]
    (fs/create-dirs root)
    (sh root "git" "init" "-q" "-b" "main")
    (init! root)
    (sh base "git" "init" "-q" "--bare" "-b" "main" origin)
    (sh base "git" "clone" "-q" origin module)
    (init! module)
    {:root root :module module :origin origin :base base}))

(defn write! [dir path content]
  (let [f (fs/path dir path)]
    (fs/create-dirs (fs/parent f))
    (spit (str f) content)))

(defn commit!
  "Stages everything in dir and commits; returns the new sha."
  [dir message]
  (sh dir "git" "add" "-A")
  (sh dir "git" "commit" "-q" "--allow-empty" "-m" message)
  (sh dir "git" "rev-parse" "HEAD"))

(defn push! [dir] (sh dir "git" "push" "-q" "origin" "HEAD:main"))

(defn module-main!
  "Commits files to the module's main and pushes. Returns the sha."
  [{:keys [module]} files message]
  (if (zero? (:exit (p/shell {:dir module :out :string :err :string :continue true} "git" "rev-parse" "-q" "--verify" "main")))
    (sh module "git" "checkout" "-q" "main")
    (sh module "git" "checkout" "-q" "--orphan" "main"))
  (doseq [[path content] files] (write! module path content))
  (let [sha (commit! module message)]
    (push! module)
    sha))

(defn bean-path [{:keys [root]} id] (str (fs/path root ".beans" (str id "--relay-logbook.md"))))

(def landed-sha "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c")

(defn bean-markdown
  "Bean markdown with an Acceptance section. :status defaults to todo; :gated?
   adds a feature-baseline line; :note appends a trailing line."
  [id {:keys [status gated? note]}]
  (str "---\n# " id "\ntitle: Relay reaches the logbook\nstatus: " (or status "todo") "\n---\n\n"
       "Relayed messages land in the logbook.\n\n"
       "## Acceptance\n\n```\nbb features " feature "\n```\n\n## Exceptions\n\n(none)\n"
       (when gated? (str "\nfeature-baseline: " repo " " landed-sha "\n"))
       (when note (str "\n" note "\n"))))

(defn bean!
  "Writes and commits a bean with an Acceptance section."
  [{:keys [root] :as w} id]
  (write! root (str ".beans/" id "--relay-logbook.md") (bean-markdown id nil))
  (commit! root (str "plan: " id)))

(defn write-bean!
  "Writes (without committing) a bean with the given :status / :gated? / :note."
  [{:keys [root]} id opts]
  (write! root (str ".beans/" id "--relay-logbook.md") (bean-markdown id opts)))

(defn bean-at!
  "Writes and commits a bean with the given :status / :gated? / :note; returns the sha."
  [{:keys [root] :as w} id opts]
  (write-bean! w id opts)
  (commit! root (str id ": " (or (:status opts) "todo"))))

(defn delete-bean! [{:keys [root] :as w} id]
  (fs/delete (bean-path w id))
  (commit! root (str id ": deleted")))

(defn bean-text [w id] (slurp (bean-path w id)))

(defn append-bean! [w id text] (spit (bean-path w id) text :append true))

(defn edit-bean! [w id f] (spit (bean-path w id) (f (bean-text w id))))

(defn work-branch!
  "Checks out bean/<id> from origin/main in the module and applies f to the feature text."
  [{:keys [module]} id f]
  (sh module "git" "fetch" "-q" "origin")
  (sh module "git" "checkout" "-q" "-B" (str "bean/" id) "origin/main")
  (let [path (str (fs/path module feature))]
    (spit path (f (slurp path))))
  (commit! module (str id ": work")))

(defn unwip [text] (str/replace-first text "  @wip\n" ""))

(defn squash-land!
  "Squash-merges bean/<id> onto the module's main, pushes, returns the squash sha."
  [{:keys [module]} id]
  (sh module "git" "checkout" "-q" "main")
  (sh module "git" "pull" "-q" "--ff-only" "origin" "main")
  (sh module "git" "merge" "-q" "--squash" (str "bean/" id))
  (let [sha (commit! module (str id ": landed"))]
    (push! module)
    sha))
