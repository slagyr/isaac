@slow
Feature: Config hot-reload integration
  End-to-end coverage of the real file-change path: actual disk
  (not an in-memory fs) and the real watcher (JVM WatchService
  under clojure, pod-babashka-filewatcher under bb). Proves that
  the two runtimes agree on the hot-reload contract and that a
  change written to disk is observed asynchronously by the server.

  Marked @slow because it writes to a temp directory and waits on
  asynchronous notifications with a real timeout (seconds, not
  milliseconds).

  Background:
    Given an empty Isaac state directory "/tmp/isaac-hot-reload-integration"
    And config:
      | key        | value  |
      | log.output | memory |
    And the isaac EDN file "config/models/grover.edn" exists with:
      | path           | value  |
      | model          | echo   |
      | provider       | grover |
      | context-window | 32768  |
    And the isaac EDN file "config/providers/grover.edn" exists with:
      | path | value  |
      | api  | grover |
    And the isaac EDN file "config/crew/marvin.edn" exists with:
      | path  | value                              |
      | model | grover                             |
      | soul  | Life? Don't talk to me about life. |
    And the Isaac server is running

  @wip
  Scenario: the real watcher picks up an on-disk change within the timeout
    When the isaac EDN file "config/crew/marvin.edn" exists with:
      | path  | value                              |
      | model | grover                             |
      | soul  | I think, therefore I am depressed. |
    Then the log has entries matching:
      | level | event            | path            |
      | :info | :config/reloaded | crew/marvin.edn |
    And the loaded config has:
      | key              | value                              |
      | crew.marvin.soul | I think, therefore I am depressed. |
