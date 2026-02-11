(ns mdm.isaac.tui.ansi-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.ansi :as ansi]))

(describe "ANSI helpers"

  (describe "color formatting"
    (it "wraps text in bold"
      (should= "\u001b[1mhello\u001b[0m" (ansi/bold "hello")))

    (it "wraps text in dim"
      (should= "\u001b[2mhello\u001b[0m" (ansi/dim "hello")))

    (it "wraps text in green"
      (should= "\u001b[32mhello\u001b[0m" (ansi/green "hello")))

    (it "wraps text in red"
      (should= "\u001b[31mhello\u001b[0m" (ansi/red "hello")))

    (it "wraps text in yellow"
      (should= "\u001b[33mhello\u001b[0m" (ansi/yellow "hello")))

    (it "wraps text in cyan"
      (should= "\u001b[36mhello\u001b[0m" (ansi/cyan "hello")))

    (it "wraps text in blue"
      (should= "\u001b[34mhello\u001b[0m" (ansi/blue "hello")))

    (it "wraps text in white on blue (inverse header)"
      (let [result (ansi/inverse "hello")]
        (should-contain "\u001b[7m" result)
        (should-contain "hello" result)
        (should-contain "\u001b[0m" result))))

  (describe "utility functions"
    (it "pad-right pads text to specified width"
      (should= "hi   " (ansi/pad-right "hi" 5)))

    (it "pad-right truncates text that exceeds width"
      (should= "hel" (ansi/pad-right "hello" 3)))

    (it "pad-right pads ANSI-colored text based on visible length"
      (let [colored (ansi/green "hi")]
        (should= 5 (ansi/visible-length (ansi/pad-right colored 5)))))

    (it "pad-right handles exact width"
      (should= "hello" (ansi/pad-right "hello" 5)))

    (it "horizontal-line creates a line of specified width"
      (should= "----------" (ansi/horizontal-line 10)))

    (it "blank-line creates spaces of specified width"
      (should= "          " (ansi/blank-line 10)))

    (it "strip-ansi removes ANSI escape codes for length calculation"
      (should= "hello" (ansi/strip-ansi "\u001b[32mhello\u001b[0m")))

    (it "visible-length returns length without ANSI codes"
      (should= 5 (ansi/visible-length "\u001b[32mhello\u001b[0m")))

    (it "visible-length returns correct length for plain text"
      (should= 5 (ansi/visible-length "hello")))

    (it "move-to generates cursor position escape"
      (should= "\u001b[5;1H" (ansi/move-to 5 1)))

    (it "clear-line is the ANSI clear-to-end-of-line escape"
      (should= "\u001b[K" ansi/clear-line))))
