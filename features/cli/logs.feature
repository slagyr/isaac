Feature: isaac logs — colorized log tail
  Isaac writes structured EDN logs that are hard to scan by eye. The
  'isaac logs' subcommand tails the configured log file and prints one
  colorized line per entry. The on-disk format is unchanged; the viewer
  is read-only.

  The subcommand defaults to follow mode (like 'tail -f'). Pass
  --no-follow to read the file once and exit. The path is taken from
  log.output in config; --file overrides it. ANSI behavior
  (--color=never / always / auto) is covered by spec/isaac/log_viewer_spec.clj.

  Background:
    Given an empty Isaac state directory "target/test-logs"

  Scenario: Renders time, level, and event in fixed columns
    Given a file "app.log" exists with content "{:ts \"2026-05-12T15:24:51.491Z\", :level :info, :event :server/started, :port 8080}"
    When isaac is run with "logs --file app.log --no-follow --color=never"
    Then the stdout matches:
      | pattern                                                      |
      | \d{2}:\d{2}:\d{2}\.\d{3}  INFO   :server/started  port=8080 |

  Scenario: Level column is fixed-width across severities
    Given a file "app.log" exists with content:
      """
      {:ts "2026-05-12T15:24:51Z", :level :info,  :event :a}
      {:ts "2026-05-12T15:24:52Z", :level :error, :event :b}
      {:ts "2026-05-12T15:24:53Z", :level :warn,  :event :c}
      {:ts "2026-05-12T15:24:54Z", :level :debug, :event :d}
      {:ts "2026-05-12T15:24:55Z", :level :trace, :event :e}
      """
    When isaac is run with "logs --file app.log --no-follow --color=never"
    Then the stdout matches:
      | pattern        |
      | INFO   :a      |
      | ERROR  :b      |
      | WARN   :c      |
      | DEBUG  :d      |
      | TRACE  :e      |

  Scenario: :file and :line are dropped from the inline display
    Given a file "app.log" exists with content "{:ts \"2026-05-12T15:24:51Z\", :level :info, :event :hello, :file \"src/x.clj\", :line 42}"
    When isaac is run with "logs --file app.log --no-follow --color=never"
    Then the stdout does not contain "file="
    And the stdout does not contain "line="
    And the stdout does not contain "src/x.clj"

  Scenario: Unparseable lines pass through as raw text
    Given a file "app.log" exists with content "this is not edn"
    When isaac is run with "logs --file app.log --no-follow --color=never"
    Then the stdout contains "this is not edn"

  Scenario: Follow mode picks up entries appended after startup
    Given a file "app.log" exists with content "{:ts \"2026-05-12T15:24:51Z\", :level :info, :event :first}"
    When isaac is run in the background with "logs --file app.log --color=never"
    And the stdout eventually contains ":first"
    And the file "app.log" is appended with "{:ts \"2026-05-12T15:24:52Z\", :level :info, :event :second}"
    Then the stdout eventually contains ":second"

  Scenario: Reads log path from log.output in config when --file is absent
    Given the isaac file "config/isaac.edn" exists with:
      """
      {:log {:output "cfg-test.log"}}
      """
    And a file "cfg-test.log" exists with content "{:ts \"2026-05-12T00:00:00Z\", :level :info, :event :via/config}"
    When isaac is run with "logs --no-follow --color=never"
    Then the stdout contains ":via/config"

  Scenario: isaac server --logs prints log entries while serving
    Given config:
      | key               | value   |
      | log.output        | app.log |
      | server.hot-reload | false   |
      | server.port       | 0       |
    When isaac is run in the background with "server --logs --color=never"
    Then the stdout eventually contains ":server/started"
