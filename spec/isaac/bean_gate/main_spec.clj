(ns isaac.bean-gate.main-spec
  "The baseline is what makes a bean ready (isaac-ctsf): `baseline` promotes the
   bean to todo, and `ready` is the dispatch check — todo AND baselined."
  (:require [clojure.string :as str]
            [isaac.bean-gate.bean :as bean]
            [isaac.bean-gate.fixture :as f]
            [isaac.bean-gate.main :as sut]
            [speclj.core :refer :all]))

(def id "isaac-mrg1")

(defn- run
  "Runs the CLI against w's isaac clone; returns {:exit :out}."
  [w & args]
  (let [exit (atom nil)
        out  (with-out-str (reset! exit (sut/run args {:root (:root w)})))]
    {:exit @exit :out out}))

(defn- world-with
  "The relay feature on module main and a committed bean at opts."
  [opts]
  (let [w (f/world!)]
    (f/module-main! w {f/feature f/relay-feature} "plan: relay @wip")
    (f/bean-at! w id opts)
    w))

(defn- baseline [w] (run w "baseline" id (str f/repo ":" f/feature)))

(defn- status [w] (bean/status (f/bean-text w id)))

(defn- gated? [w] (bean/gated? (f/bean-text w id)))

(describe "bean-gate baseline promotes"

  (it "writes the gate lines and promotes a draft bean to todo"
    (let [w (world-with {:status "draft"})]
      (should= 0 (:exit (baseline w)))
      (should (gated? w))
      (should= "todo" (status w))))

  (it "leaves a todo bean todo"
    (let [w (world-with {:status "todo"})]
      (should= 0 (:exit (baseline w)))
      (should (gated? w))
      (should= "todo" (status w))))

  (for [s ["in-progress" "completed" "scrapped"]]
    (it (str "refuses a " s " bean with exit 2 and leaves the file byte-identical")
      (let [w      (world-with {:status s})
            before (f/bean-text w id)
            {:keys [exit out]} (baseline w)]
        (should= 2 exit)
        (should-contain (str "status " s) out)
        (should= before (f/bean-text w id)))))

  (it "refuses no refs with exit 2 and touches nothing"
    (let [w      (world-with {:status "draft"})
          before (f/bean-text w id)
          {:keys [exit out]} (run w "baseline" id)]
      (should= 2 exit)
      (should-contain "a bean is baselined against scenarios; none given" out)
      (should= before (f/bean-text w id))
      (should= "draft" (status w)))))

(describe "bean-gate ready"

  (it "is 0 for a todo bean with a baseline line"
    (let [w (world-with {:status "todo" :gated? true})
          {:keys [exit out]} (run w "ready" id)]
      (should= 0 exit)
      (should-contain (str id ": ready") out)))

  (it "ignores a literal wildcard notes file and reads the real bean"
    (let [w (world-with {:status "todo" :gated? true})]
      (f/write! (:root w) (str ".beans/" id "--*.md") "worker notes\n")
      (should= (f/bean-path w id) (bean/bean-file (:root w) id))
      (should= 0 (:exit (run w "ready" id)))))

  (it "is 1 \"not baselined\" for a todo bean without one"
    (let [{:keys [exit out]} (run (world-with {:status "todo"}) "ready" id)]
      (should= 1 exit)
      (should-contain "not baselined" out)
      (should= 1 (count (str/split-lines out)))))

  (it "is 1 \"status draft\" for a baselined draft bean"
    (let [{:keys [exit out]} (run (world-with {:status "draft" :gated? true}) "ready" id)]
      (should= 1 exit)
      (should-contain "status draft" out)))

  (it "is 1 for a bean that does not exist"
    (let [{:keys [exit out]} (run (f/world!) "ready" "isaac-nope")]
      (should= 1 exit)
      (should-contain "no bean file" out)))

  (it "is 2 with no bean id"
    (should= 2 (:exit (run (f/world!) "ready"))))

  (it "is a pure read"
    (let [w      (world-with {:status "draft" :gated? true})
          before (f/bean-text w id)]
      (run w "ready" id)
      (should= before (f/bean-text w id)))))
