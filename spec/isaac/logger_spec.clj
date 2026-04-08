(ns isaac.logger-spec
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.logger :as sut]
    [speclj.core :refer :all]))

(def test-log "/tmp/isaac-test.log")

(defn- read-entries []
  (when (.exists (clojure.java.io/file test-log))
    (let [lines (remove str/blank? (str/split-lines (slurp test-log)))]
      (mapv edn/read-string lines))))

(describe "Logger"

  (before (spit test-log "" :append false)
          (sut/set-level! :debug)
          (sut/set-log-file! test-log))
  (after  (sut/set-log-file! "/tmp/isaac.log"))

  ;; region ----- Writing Entries -----

  (describe "log entry format"

    (it "writes a single EDN line per log call"
      (sut/info {:event :test/hello})
      (let [lines (str/split-lines (slurp test-log))]
        (should= 1 (count lines))))

    (it "each entry is a readable EDN map"
      (sut/info {:event :test/hello :value 42})
      (let [entry (first (read-entries))]
        (should (map? entry))
        (should= :test/hello (:event entry))
        (should= 42 (:value entry))))

    (it "includes a numeric timestamp"
      (sut/info {:event :test/ts})
      (let [entry (first (read-entries))]
        (should (number? (:ts entry)))
        (should (> (:ts entry) 0))))

    (it "includes the log level"
      (sut/warn {:event :test/level})
      (let [entry (first (read-entries))]
        (should= :warn (:level entry))))

    (it "includes the source file"
      (sut/info {:event :test/file})
      (let [entry (first (read-entries))]
        (should (string? (:file entry)))
        (should (str/ends-with? (:file entry) ".clj"))))

    (it "includes the source line"
      (sut/info {:event :test/line})
      (let [entry (first (read-entries))]
        (should (number? (:line entry)))
        (should (> (:line entry) 0))))

    (it "appends multiple entries without overwriting"
      (sut/info {:event :test/first})
      (sut/info {:event :test/second})
      (let [entries (read-entries)]
        (should= 2 (count entries))
        (should= :test/first  (:event (first entries)))
        (should= :test/second (:event (second entries))))))

  ;; endregion ^^^^^ Writing Entries ^^^^^

  ;; region ----- Level Macros -----

  (describe "level macros"

    (it "error logs with :error level"
      (sut/error {:event :test/e})
      (should= :error (:level (first (read-entries)))))

    (it "warn logs with :warn level"
      (sut/warn {:event :test/w})
      (should= :warn (:level (first (read-entries)))))

    (it "report logs with :report level"
      (sut/report {:event :test/r})
      (should= :report (:level (first (read-entries)))))

    (it "info logs with :info level"
      (sut/info {:event :test/i})
      (should= :info (:level (first (read-entries)))))

    (it "debug logs with :debug level"
      (sut/debug {:event :test/d})
      (should= :debug (:level (first (read-entries))))))

  ;; endregion ^^^^^ Level Macros ^^^^^

  ;; region ----- Level Filtering -----

  (describe "level filtering"

    (it "logs entries at or above the configured level"
      (sut/set-level! :warn)
      (sut/error {:event :test/err})
      (sut/warn {:event :test/wrn})
      (sut/info {:event :test/inf})
      (let [entries (read-entries)]
        (should= 2 (count entries))
        (should= #{:error :warn} (set (map :level entries)))))

    (it "logs nothing when level is above all entries"
      (sut/set-level! :error)
      (sut/warn {:event :test/w})
      (sut/info {:event :test/i})
      (sut/debug {:event :test/d})
      (should= 0 (count (read-entries))))

    (it "logs all entries when level is :debug"
      (sut/set-level! :debug)
      (sut/error {:event :test/e})
      (sut/warn {:event :test/w})
      (sut/report {:event :test/r})
      (sut/info {:event :test/i})
      (sut/debug {:event :test/d})
      (should= 5 (count (read-entries))))

    (it "includes :report level between :warn and :info"
      (sut/set-level! :report)
      (sut/error {:event :test/e})
      (sut/warn {:event :test/w})
      (sut/report {:event :test/r})
      (sut/info {:event :test/i})
      (sut/debug {:event :test/d})
      (let [entries (read-entries)]
        (should= 3 (count entries))
        (should= #{:error :warn :report} (set (map :level entries)))))))

  ;; endregion ^^^^^ Level Filtering ^^^^^
