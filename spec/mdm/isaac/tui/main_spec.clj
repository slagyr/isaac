(ns mdm.isaac.tui.main-spec
  (:require [mdm.isaac.tui.main :as sut]
            [speclj.core :refer :all]))

(describe "tui.main"

  (context "render-output"

    (it "passes through content (view handles cursor positioning)"
      (let [output (sut/render-output "Hello World")]
        (should= "Hello World" output)))

    (it "handles empty content"
      (let [output (sut/render-output "")]
        (should= "" output)))))
