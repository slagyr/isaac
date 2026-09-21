(ns isaac.bean-gate.ref-spec
  "The gate checks whatever ref the sibling checkout is parked on. It must say
   which, on every verdict, and warn when that is another bean's branch
   (isaac-9yms)."
  (:require [clojure.string :as str]
            [isaac.bean-gate.core :as sut]
            [isaac.bean-gate.fixture :as f]
            [speclj.core :refer :all]))

(def ^:private id "isaac-mrg1")

(defn- gated-world []
  (let [w (f/world!)]
    (f/module-main! w {f/feature f/relay-feature} "plan: relay @wip")
    (f/bean! w id)
    (sut/baseline {:root (:root w) :id id :specs [{:repo f/repo :path f/feature}]})
    (f/commit! (:root w) (str "plan: baseline " id))
    w))

(defn- verify [w & [opts]] (sut/verify (merge {:root (:root w) :id id} opts)))

(defn- joined [xs] (str/join " | " xs))

(describe "the ref the gate checked"

  (it "names the branch the checkout is parked on, in the verdict"
    (let [w (gated-world)]
      (f/work-branch! w "isaac-other" #(str/replace % "@wip\n" ""))
      (let [{:keys [checked]} (verify w)]
        (should-contain "branch bean/isaac-other" (joined checked))
        (should-contain f/repo (joined checked)))))

  (it "warns that the parked branch is not this bean's"
    (let [w (gated-world)]
      (f/work-branch! w "isaac-other" #(str/replace % "@wip\n" ""))
      (let [{:keys [notes]} (verify w)]
        (should-contain "bean/isaac-other" (joined notes))
        (should-contain "--ref" (joined notes)))))

  (it "stays quiet when the checkout is on this bean's own branch"
    (let [w (gated-world)]
      (f/work-branch! w id #(str/replace % "@wip\n" ""))
      (let [{:keys [notes]} (verify w)]
        (should-not-contain "--ref" (joined notes)))))

  (it "stays quiet when --ref was given"
    (let [w (gated-world)]
      (f/work-branch! w "isaac-other" #(str/replace % "@wip\n" ""))
      (let [{:keys [notes]} (verify w {:refs {f/repo "origin/main"}})]
        (should-not-contain "--ref" (joined notes)))))

  (it "names the explicit ref it was given"
    (let [w (gated-world)]
      (f/work-branch! w "isaac-other" #(str/replace % "@wip\n" ""))
      (let [{:keys [checked]} (verify w {:refs {f/repo "origin/main"}})]
        (should-contain "origin/main" (joined checked))))))
