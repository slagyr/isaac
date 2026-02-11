(ns mdm.isaac.tui.main-spec
  (:require [mdm.isaac.tui.main :as sut]
            [speclj.core :refer :all]))

(describe "tui.main"

  (context "render-output"

    (it "returns cursor-home, content, then clear-to-end (flicker-free order)"
      (let [output (sut/render-output "Hello World")]
        ;; Should start with cursor home
        (should (.startsWith output "\u001b[H"))
        ;; Content should come before any screen clearing
        (should-contain "Hello World" output)
        ;; Should end with clear-to-end-of-screen, not have clear-screen before content
        (should-not-contain "\u001b[2J" output)  ;; No full screen clear
        (should (.endsWith output "\u001b[J")))) ;; Clear from cursor to end

    (it "handles empty content"
      (let [output (sut/render-output "")]
        (should= "\u001b[H\u001b[J" output)))

    (it "handles multiline content"
      (let [output (sut/render-output "Line 1\nLine 2\nLine 3")]
        (should (.startsWith output "\u001b[H"))
        (should-contain "Line 1\nLine 2\nLine 3" output)
        (should (.endsWith output "\u001b[J"))))))
