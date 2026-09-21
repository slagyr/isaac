(ns isaac.bean-gate.bean-spec
  "Gate lines are contract. A bean that merely *quotes* one — indented as a code
   block, or inside a ``` fence — must not be gated by it (isaac-dopm)."
  (:require [isaac.bean-gate.bean :as sut]
            [speclj.core :refer :all]))

(def ^:private sha "f031ff2dcdabe681f6c75ac8e367e44d3a7960b0")
(def ^:private blob "e2f95fd2d79b2363297b1ee7fc757c2abbcf3f22")

(defn- bean-text [body]
  (str "---\n# isaac-mrg1\nstatus: todo\n---\n\n" body))

(describe "bean gate lines"

  (context "a baseline at column 0"

    (it "gates the bean"
      (let [text (bean-text (str "Some prose.\n\nfeature-baseline: isaac-marigold " sha "\n"))]
        (should= true (sut/gated? text))
        (should= {:kind :baseline :repo "isaac-marigold" :sha sha}
                 (get-in (sut/in-force text) [:baselines "isaac-marigold"]))))

    (it "records its blob"
      (let [text (bean-text (str "feature-baseline: isaac-marigold " sha "\n"
                                 "feature-blob: isaac-marigold features/a.feature " blob " 90\n"))]
        (should= [{:kind :blob :repo "isaac-marigold" :path "features/a.feature"
                   :blob blob :lines [90]}]
                 (:blobs (sut/in-force text)))))

    (it "is a contract line"
      (let [text (bean-text (str "feature-baseline: isaac-marigold " sha "\n"))]
        (should-contain [:gate (str "feature-baseline: isaac-marigold " sha)]
                        (sut/contract-lines text)))))

  (context "a baseline quoted in prose"

    (it "does not gate the bean when indented as a code block"
      (let [text (bean-text (str "The planner supplied this baseline:\n\n"
                                 "    feature-baseline: isaac-marigold " sha "\n"
                                 "    feature-blob: isaac-marigold features/a.feature " blob " 90\n"))]
        (should= false (sut/gated? text))
        (should= [] (vec (sut/gate-lines text)))))

    (it "does not gate the bean when fenced"
      (let [text (bean-text (str "The planner supplied this baseline:\n\n"
                                 "```\n"
                                 "feature-baseline: isaac-marigold " sha "\n"
                                 "```\n"))]
        (should= false (sut/gated? text))
        (should= [] (vec (sut/gate-lines text)))))

    (it "does not freeze it as a contract line"
      (let [text (bean-text (str "Narrative citing another bean:\n\n"
                                 "    feature-blob: isaac-marigold features/a.feature " blob " 90\n"))]
        (should= #{} (sut/contract-lines text))))

    (it "leaves a real baseline in force alongside a quoted one"
      (let [text (bean-text (str "feature-baseline: isaac-marigold " sha "\n\n"
                                 "For contrast, isaac-mrg2 was baselined at:\n\n"
                                 "    feature-baseline: isaac-marigold deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n"))]
        (should= true (sut/gated? text))
        (should= sha (get-in (sut/in-force text) [:baselines "isaac-marigold" :sha]))))))
