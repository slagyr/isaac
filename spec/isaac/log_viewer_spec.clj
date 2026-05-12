(ns isaac.log-viewer-spec
  (:require
    [clojure.string :as str]
    [isaac.log-viewer :as sut]
    [speclj.core :refer :all]))

(describe "Log viewer"

  (describe "format-time"

    (it "formats UTC timestamp to local HH:MM:SS.mmm"
      (let [result (sut/format-time "2026-05-12T15:24:51.491Z")]
        (should (re-matches #"\d{2}:\d{2}:\d{2}\.\d{3}" result))))

    (it "handles timestamps without sub-second precision"
      (let [result (sut/format-time "2026-05-12T15:24:51Z")]
        (should (re-matches #"\d{2}:\d{2}:\d{2}\.\d{3}" result))))

    (it "returns a 12-char string"
      (should= 12 (count (sut/format-time "2026-05-12T15:24:51.491Z")))))

  (describe "color-for-level"

    (it "returns red for :error"
      (should (str/includes? (sut/color-for-level :error) "31")))

    (it "returns yellow for :warn"
      (should (str/includes? (sut/color-for-level :warn) "33")))

    (it "returns cyan for :info"
      (should (str/includes? (sut/color-for-level :info) "36")))

    (it "returns dim for :debug"
      (should (str/includes? (sut/color-for-level :debug) "2")))

    (it "returns dim for :trace"
      (should (str/includes? (sut/color-for-level :trace) "2"))))

  (describe "color-for-value"

    (it "returns red for nil"
      (should (str/includes? (sut/color-for-value nil) "31")))

    (it "returns yellow for booleans"
      (should (str/includes? (sut/color-for-value true) "33"))
      (should (str/includes? (sut/color-for-value false) "33")))

    (it "returns green for numbers"
      (should (str/includes? (sut/color-for-value 42) "32")))

    (it "returns magenta for keywords"
      (should (str/includes? (sut/color-for-value :foo) "35")))

    (it "returns empty string for strings"
      (should= "" (sut/color-for-value "hello"))))

  (describe "color-for-ns"

    (it "is deterministic — same ns always returns same color"
      (should= (sut/color-for-ns "acp-proxy")
               (sut/color-for-ns "acp-proxy")))

    (it "returns a non-empty ANSI string"
      (should (seq (sut/color-for-ns "server")))
      (should (str/includes? (sut/color-for-ns "server") "\033["))))

  (describe "color-for-session"

    (it "is deterministic — same session always returns same color"
      (should= (sut/color-for-session "abc-123")
               (sut/color-for-session "abc-123")))

    (it "returns a non-empty ANSI string"
      (should (str/includes? (sut/color-for-session "xyz") "\033["))))

  (describe "format-entry"

    (it "assembles time, level, event in fixed columns"
      (let [entry  {:ts "2026-05-12T15:24:51.491Z" :level :info :event :server/started :port 8080}
            result (sut/format-entry entry false)]
        (should (re-find #"\d{2}:\d{2}:\d{2}\.\d{3}" result))
        (should (str/includes? result "INFO "))
        (should (str/includes? result ":server/started"))
        (should (str/includes? result "port=8080"))))

    (it "pads level to 5 chars"
      (doseq [[level expected] {:info "INFO " :error "ERROR" :warn "WARN " :debug "DEBUG" :trace "TRACE"}]
        (let [result (sut/format-entry {:ts "2026-05-12T00:00:00Z" :level level :event :x} false)]
          (should (str/includes? result (str expected "  "))))))

    (it "drops :file and :line from output"
      (let [result (sut/format-entry {:ts "2026-05-12T00:00:00Z" :level :info :event :x
                                      :file "src/foo.clj" :line 42} false)]
        (should-not (str/includes? result "file="))
        (should-not (str/includes? result "line="))
        (should-not (str/includes? result "src/foo.clj"))))

    (it "includes ANSI codes when color? is true"
      (let [result (sut/format-entry {:ts "2026-05-12T00:00:00Z" :level :info :event :server/started} true)]
        (should (str/includes? result "\033["))))

    (it "emits no ANSI codes when color? is false"
      (let [result (sut/format-entry {:ts "2026-05-12T00:00:00Z" :level :info :event :server/started} false)]
        (should-not (str/includes? result "\033[")))))

  (describe "format-line"

    (it "formats a valid EDN log line"
      (let [result (sut/format-line "{:ts \"2026-05-12T15:24:51.491Z\" :level :info :event :server/started}" false)]
        (should (str/includes? result ":server/started"))))

    (it "passes unparseable lines through as-is"
      (should= "this is not edn" (sut/format-line "this is not edn" false)))

    (it "returns nil for blank lines"
      (should-be-nil (sut/format-line "   " false))
      (should-be-nil (sut/format-line "" false))
      (should-be-nil (sut/format-line nil false)))))
