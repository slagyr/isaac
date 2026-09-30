(ns isaac.bean-gate.workflow-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]))

(describe "Bean Gate CI on a module whose default branch is not main"
  (it "checks origin/main rather than the clone's default HEAD"
    (let [workflow (slurp ".github/workflows/bean-gate.yml")]
      (should (str/includes? workflow "git clone --branch main --filter=blob:none"))
      (should (str/includes? workflow "--ref \"$repo=origin/main\"")))))
