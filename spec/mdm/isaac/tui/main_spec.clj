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
        (should= "" output))))

  (context "parse-sgr-mouse"

    (it "returns scroll-up for button 64 press"
      (should= {:type :scroll-up} (sut/parse-sgr-mouse "64;10;5" \M)))

    (it "returns scroll-down for button 65 press"
      (should= {:type :scroll-down} (sut/parse-sgr-mouse "65;10;5" \M)))

    (it "returns nil for regular mouse click (button 0)"
      (should-be-nil (sut/parse-sgr-mouse "0;10;5" \M)))

    (it "returns nil for mouse release events"
      (should-be-nil (sut/parse-sgr-mouse "64;10;5" \m)))

    (it "returns nil for nil data"
      (should-be-nil (sut/parse-sgr-mouse nil nil)))))
