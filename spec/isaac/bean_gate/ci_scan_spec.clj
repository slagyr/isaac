(ns isaac.bean-gate.ci-scan-spec
  (:require [isaac.bean-gate.core :as sut]
            [isaac.bean-gate.fixture :as f]
            [isaac.bean-gate.main :as main]
            [speclj.core :refer :all]))

(def gated {:status "completed" :gated? true})

(defn- scan [w before after] (sut/ci-scan {:root (:root w) :before before :after after}))

(defn- world-with
  "A world whose bean starts at opts; returns [world before-sha]."
  [id opts]
  (let [w (f/world!)]
    [w (f/bean-at! w id opts)]))

(describe "bean-gate ci-scan"

  (it "lists a bean that became completed and carries a baseline"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress" :gated? true})
          after      (f/bean-at! w "isaac-mrg1" gated)]
      (should= {:beans ["isaac-mrg1"] :skipped []} (scan w before after))))

  (it "skips a completed bean without a baseline"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress"})
          after      (f/bean-at! w "isaac-mrg1" {:status "completed"})]
      (should= {:beans [] :skipped [{:id "isaac-mrg1" :reason :not-gated}]} (scan w before after))))

  (it "skips a bean that is still in progress"
    (let [[w before] (world-with "isaac-mrg1" {:status "todo" :gated? true})
          after      (f/bean-at! w "isaac-mrg1" {:status "in-progress" :gated? true})]
      (should= {:beans [] :skipped [{:id "isaac-mrg1" :reason :not-completed}]} (scan w before after))))

  (it "lists a completed bean whose status did not change in the range"
    (let [[w before] (world-with "isaac-mrg1" gated)
          after      (f/bean-at! w "isaac-mrg1" (assoc gated :note "## Notes\n\nre-pushed"))]
      (should= ["isaac-mrg1"] (:beans (scan w before after)))))

  (it "ignores a deleted bean file"
    (let [[w before] (world-with "isaac-mrg1" gated)
          after      (f/delete-bean! w "isaac-mrg1")]
      (should= {:beans [] :skipped []} (scan w before after))))

  (it "lists every completed gated bean in one push"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress" :gated? true})]
      (f/write-bean! w "isaac-mrg1" gated)
      (f/write-bean! w "isaac-mrg2" gated)
      (f/write-bean! w "isaac-mrg3" {:status "completed"})
      (let [after (f/commit! (:root w) "land three")]
        (should= {:beans   ["isaac-mrg1" "isaac-mrg2"]
                  :skipped [{:id "isaac-mrg3" :reason :not-gated}]}
                 (scan w before after)))))

  (it "ignores files outside .beans"
    (let [[w before] (world-with "isaac-mrg1" gated)]
      (f/write! (:root w) "README.md" "hello")
      (should= {:beans [] :skipped []} (scan w before (f/commit! (:root w) "docs")))))

  (it "reports a range it cannot diff instead of throwing"
    (let [[w before] (world-with "isaac-mrg1" gated)]
      (should-contain "deadbee" (:error (scan w "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" before))))))

(describe "bean-gate ci-scan command"

  (it "prints one bean id per line and exits 0"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress" :gated? true})
          after      (f/bean-at! w "isaac-mrg1" gated)
          exit       (atom nil)
          out        (with-out-str (reset! exit (main/run ["ci-scan" before after] {:root (:root w)})))]
      (should= 0 @exit)
      (should= "isaac-mrg1\n" out)))

  (it "exits 0 with no output when nothing was gated"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress"})
          after      (f/bean-at! w "isaac-mrg1" {:status "completed"})
          exit       (atom nil)
          out        (with-out-str (reset! exit (main/run ["ci-scan" before after] {:root (:root w)})))]
      (should= 0 @exit)
      (should= "" out)))

  (it "prints beans and skips as edn under --edn"
    (let [[w before] (world-with "isaac-mrg1" {:status "in-progress" :gated? true})]
      (f/write-bean! w "isaac-mrg1" gated)
      (f/write-bean! w "isaac-mrg2" {:status "completed"})
      (let [after (f/commit! (:root w) "land two")
            out   (with-out-str (main/run ["ci-scan" before after "--edn"] {:root (:root w)}))]
        (should= {:beans ["isaac-mrg1"] :skipped [{:id "isaac-mrg2" :reason :not-gated}]}
                 (read-string out)))))

  (it "exits 0 on an unusable range"
    (let [[w before] (world-with "isaac-mrg1" gated)
          exit       (atom nil)
          out        (with-out-str (reset! exit (main/run ["ci-scan" "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" before]
                                                          {:root (:root w)})))]
      (should= 0 @exit)
      (should= "" out)))

  (it "prints usage without both shas"
    (let [w   (f/world!)
          out (with-out-str (should= 2 (main/run ["ci-scan" "HEAD"] {:root (:root w)})))]
      (should-contain "bb bean-gate ci-scan" out))))
