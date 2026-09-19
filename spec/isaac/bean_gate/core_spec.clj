(ns isaac.bean-gate.core-spec
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [isaac.bean-gate.core :as sut]
            [isaac.bean-gate.fixture :as f]
            [isaac.bean-gate.main :as main]
            [speclj.core :refer :all]))

(def id "isaac-mrg1")

(defn- baseline! [w & [spec]]
  (sut/baseline {:root (:root w) :id id :specs [(merge {:repo f/repo :path f/feature} spec)]}))

(defn- gated-world
  "Relay feature on module main, bean committed, baseline recorded and committed by the planner."
  []
  (let [w (f/world!)]
    (f/module-main! w {f/feature f/relay-feature} "plan: relay @wip")
    (f/bean! w id)
    (baseline! w)
    (f/commit! (:root w) (str "plan: baseline " id))
    w))

(defn- verify [w & [opts]] (sut/verify (merge {:root (:root w) :id id} opts)))

(defn- failures [result] (str/join "\n" (:failures result)))

(defn- with-out-str-and-exit [f]
  (let [exit (atom nil)]
    (with-out-str (reset! exit (f)))
    @exit))

(describe "bean-gate baseline"

  (it "appends one feature-baseline line and one feature-blob line"
    (let [w   (f/world!)
          sha (f/module-main! w {f/feature f/relay-feature} "plan: relay @wip")
          _   (f/bean! w id)
          {:keys [ok? lines]} (baseline! w)
          blob (f/sh (:module w) "git" "rev-parse" (str sha ":" f/feature))]
      (should ok?)
      (should= [(str "feature-baseline: " f/repo " " sha)
                (str "feature-blob: " f/repo " " f/feature " " blob)]
               lines)
      (should (str/ends-with? (f/bean-text w id) (str (str/join "\n" lines) "\n")))))

  (it "records the bean's scenarios when lines are named"
    (let [w (f/world!)]
      (f/module-main! w {f/feature f/relay-feature} "plan")
      (f/bean! w id)
      (should (str/ends-with? (last (:lines (baseline! w {:lines [f/relayed-line]}))) (str " " f/relayed-line)))))

  (it "refuses a named line that is not a @wip scenario"
    (let [w (f/world!)]
      (f/module-main! w {f/feature f/relay-feature} "plan")
      (f/bean! w id)
      (let [{:keys [ok? errors]} (baseline! w {:lines [12 5]})]
        (should-not ok?)
        (should-contain "12 — scenario is not @wip" (str/join "\n" errors))
        (should-contain "5 — no Scenario keyword" (str/join "\n" errors)))))

  (it "refuses a file with no @wip scenario and leaves the bean untouched"
    (let [w (f/world!)]
      (f/module-main! w {f/feature (f/unwip f/relay-feature)} "plan")
      (f/bean! w id)
      (let [before (f/bean-text w id)
            {:keys [ok? errors]} (baseline! w)]
        (should-not ok?)
        (should-contain "no @wip scenario" (first errors))
        (should= before (f/bean-text w id)))))

  (it "refuses a file that is not on origin/main"
    (let [w (f/world!)]
      (f/module-main! w {f/feature f/relay-feature} "plan")
      (f/bean! w id)
      (should-contain "not on origin/main" (first (:errors (baseline! w {:path "features/missing.feature"}))))))

  (it "finds the module through a --dir override"
    (let [w     (f/world!)
          _     (f/module-main! w {f/feature f/relay-feature} "plan")
          moved (str (fs/path (:base w) "elsewhere"))]
      (f/bean! w id)
      (fs/move (:module w) moved)
      (should-contain "no git checkout" (first (:errors (baseline! w))))
      (should (:ok? (sut/baseline {:root (:root w) :id id :dirs {f/repo moved}
                                   :specs [{:repo f/repo :path f/feature}]}))))))

(describe "bean-gate verify"

  (it "reports a bean without a baseline as not gated"
    (let [w (f/world!)]
      (f/bean! w id)
      (should= :ungated (:status (verify w)))))

  (it "passes a worker branch that only removes @wip"
    (let [w (gated-world)]
      (f/work-branch! w id f/unwip)
      (should= [] (:failures (verify w)))
      (should= :pass (:status (verify w)))))

  (it "passes when another bean later adds scenarios to the same file"
    (let [w     (gated-world)
          other (str f/relay-feature "\n  @wip\n  Scenario: a relayed message is timestamped\n    When Cordelia relays \"dawn\"\n    Then the logbook entry has a timestamp\n\n  Scenario: relays are counted\n    Then the relay count is 0\n")]
      (f/module-main! w {f/feature other} "plan: another bean")
      (f/work-branch! w id f/unwip)
      (should= :pass (:status (verify w)))))

  (it "fails a reworded step"
    (let [w (gated-world)]
      (f/work-branch! w id #(-> % f/unwip (str/replace "Then the logbook contains \"tide report\"" "Then the logbook is not empty")))
      (let [r (verify w)]
        (should= :fail (:status r))
        (should-contain "\"Scenario: a relayed message reaches the logbook\" (baseline line 8) was changed" (failures r))
        (should-contain "- Then the logbook contains \"tide report\"" (failures r))
        (should-contain "beyond @wip removal" (failures r)))))

  (it "fails a deleted baselined scenario"
    (let [w (gated-world)]
      (f/work-branch! w id #(-> % f/unwip (subs 0 (str/index-of % "  Scenario: an empty"))))
      (should-contain "\"Scenario: an empty message is refused\" (baseline line 12) is missing" (failures (verify w)))))

  (it "fails a scenario re-tagged @slow"
    (let [w (gated-world)]
      (f/work-branch! w id #(str/replace % "  @wip\n" "  @slow\n"))
      (should-contain "was changed" (failures (verify w)))))

  (it "fails a step inserted into a baselined scenario"
    (let [w (gated-world)]
      (f/work-branch! w id #(-> % f/unwip (str/replace "    When Cordelia relays \"tide report\"\n"
                                                       "    Given the logbook is empty\n    When Cordelia relays \"tide report\"\n")))
      (should-contain "+ Given the logbook is empty" (failures (verify w)))))

  (it "fails when the bean's scenario still carries @wip"
    (let [w (gated-world)]
      (f/work-branch! w id identity)
      (should-contain "\"Scenario: a relayed message reaches the logbook\" still carries @wip" (failures (verify w)))))

  (it "fails a worker edit to a feature file the bean did not baseline"
    (let [w (gated-world)]
      (f/module-main! w {"features/logbook.feature" "Feature: Logbook\n\n  Scenario: entries are kept\n    Then the logbook keeps 10 entries\n"} "plan: logbook")
      (f/work-branch! w id f/unwip)
      (f/write! (:module w) "features/logbook.feature" "Feature: Logbook\n\n  Scenario: entries are kept\n    Then the logbook keeps 1 entry\n")
      (f/commit! (:module w) "loosen")
      (should-contain "features/logbook.feature: worker diff" (failures (verify w)))
      (should-contain "did not baseline" (failures (verify w)))))

  (it "fails when a contract line is edited after the baseline"
    (let [w (gated-world)]
      (f/work-branch! w id f/unwip)
      (f/edit-bean! w id #(str/replace % (str "bb features " f/feature) "bb spec"))
      (let [sha (f/commit! (:root w) "soften acceptance")]
        (should-contain (str "contract line removed or edited in " (subs sha 0 7) ": bb features " f/feature)
                        (failures (verify w))))))

  (it "fails when a baseline line is removed, even uncommitted"
    (let [w (gated-world)]
      (f/work-branch! w id f/unwip)
      (f/edit-bean! w id #(str/replace % #"(?m)^feature-blob:.*\n" ""))
      (should-contain "contract line removed or edited in working tree: feature-blob:" (failures (verify w)))))

  (it "passes when contract lines are appended"
    (let [w (gated-world)]
      (f/work-branch! w id f/unwip)
      (f/edit-bean! w id #(str/replace % "(none)\n" "(none)\n- planner note: relay timing is out of scope\n"))
      (f/commit! (:root w) "exception")
      (f/append-bean! w id "\n## Worker notes\n\nDone; landed next.\n")
      (should= :pass (:status (verify w)))))

  (it "fails a baseline committed from a worker session"
    (let [w (f/world!)]
      (f/module-main! w {f/feature f/relay-feature} "plan")
      (f/bean! w id)
      (baseline! w)
      (f/commit! (:root w) "rebaseline\n\nIsaac-Session: isaac-work-1")
      (f/work-branch! w id f/unwip)
      (should-contain "worker/verifier session commit" (failures (verify w)))))

  (it "uses the newest baseline after a planner re-baseline"
    (let [w       (gated-world)
          revised (str/replace f/relay-feature "Then the logbook contains \"tide report\"" "Then the logbook contains exactly \"tide report\"")]
      (f/module-main! w {f/feature revised} "plan: tighten relay")
      (baseline! w)
      (f/commit! (:root w) "plan: re-baseline")
      (f/work-branch! w id f/unwip)
      (should= :pass (:status (verify w)))))

  (it "treats a whole-feature @wip as the bean's"
    (let [w (f/world!)]
      (f/module-main! w {f/feature (str "@wip\n" (f/unwip f/relay-feature))} "plan")
      (f/bean! w id)
      (baseline! w)
      (f/commit! (:root w) "plan: baseline")
      (f/work-branch! w id identity)
      (should-contain "the Feature line still carries @wip" (failures (verify w)))
      (f/work-branch! w id #(str/replace-first % "@wip\n" ""))
      (should= :pass (:status (verify w)))))

  (context "after landing"

    (it "checks the squash commit named by main-sha"
      (let [w (gated-world)]
        (f/work-branch! w id f/unwip)
        (let [sha (f/squash-land! w id)]
          (f/append-bean! w id (str "\n## Landed on main\n\nmain-sha: " f/repo " " sha "\n"))
          (f/commit! (:root w) "landed")
          (let [r (verify w)]
            (should= :pass (:status r))
            (should= [(str f/repo " @ main-sha " (subs sha 0 7))] (:checked r))))))

    (it "fails a squash commit that edits the feature beyond @wip"
      (let [w (gated-world)]
        (f/work-branch! w id #(-> % f/unwip (str/replace "nothing to send" "empty")))
        (let [sha (f/squash-land! w id)]
          (f/append-bean! w id (str "\nmain-sha: " f/repo " " sha "\n"))
          (should-contain "beyond @wip removal" (failures (verify w))))))

    (it "fails a main-sha that is not on origin/main"
      (let [w (gated-world)
            sha (f/work-branch! w id f/unwip)]
        (f/append-bean! w id (str "\nmain-sha: " f/repo " " sha "\n"))
        (should-contain "is not on origin/main" (failures (verify w)))))))

(describe "bean-gate command"

  (it "exits 0 on pass, 1 on fail, 2 when not gated"
    (let [w (gated-world)]
      (f/work-branch! w id f/unwip)
      (should= 0 (with-out-str-and-exit #(main/run ["verify" id] {:root (:root w)})))
      (f/work-branch! w id identity)
      (should= 1 (with-out-str-and-exit #(main/run ["verify" id] {:root (:root w)})))
      (f/bean! w "isaac-mrg2")
      (should= 2 (with-out-str-and-exit #(main/run ["verify" "isaac-mrg2"] {:root (:root w)})))))

  (it "prints usage for --help"
    (let [out (with-out-str (should= 0 (main/run ["--help"])))]
      (should-contain "bb bean-gate baseline <bean-id>" out)
      (should-contain "bb bean-gate verify <bean-id>" out))))
