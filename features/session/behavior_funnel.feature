@wip
Feature: Canonical session-behavior resolution funnel
  All session-behavior reads route through a single `resolve-behavior`
  function. Each field is either *state-defining* (cascade resolves once
  at session creation; result is locked onto the sidecar) or *behavioral*
  (cascade resolves fresh on every call). The funnel encodes the
  locked-vs-cascade distinction in one place.

  Tracked by isaac-bv48.

  Background:
    Given an empty Isaac state directory "/test"

  Scenario Outline: Cascade precedence resolves the right <field> at creation
    Given the EDN isaac file "isaac.edn" exists with:
      | path                   | value      |
      | defaults.<field>       | <defaults> |
      | crew.main.<field>      | <crew>     |
      | models.spark.<field>   | <model>    |
      | providers.test.<field> | <provider> |
    When a session "s" is created with explicit <field> "<creator>"
    Then the resolved behavior for "s" matches:
      | key     | value      |
      | <field> | <expected> |

    Examples:
      | field             | creator           | crew                | model             | provider          | defaults          | expected                                                       |
      | history-retention |                   |                     |                   |                   |                   | :retain                                                        |
      | history-retention |                   |                     |                   |                   | :prune            | :prune                                                         |
      | history-retention |                   |                     |                   | :retain           | :prune            | :retain                                                        |
      | history-retention |                   |                     | :prune            | :retain           | :retain           | :prune                                                         |
      | history-retention |                   | :retain             | :prune            | :prune            | :prune            | :retain                                                        |
      | history-retention | :prune            | :retain             | :retain           | :retain           | :retain           | :prune                                                         |
      | effort            |                   |                     |                   |                   |                   | 7                                                              |
      | effort            |                   |                     |                   |                   | 5                 | 5                                                              |
      | effort            |                   |                     |                   | 6                 | 5                 | 6                                                              |
      | effort            |                   |                     | 7                 | 6                 | 5                 | 7                                                              |
      | effort            |                   | 8                   | 7                 | 6                 | 5                 | 8                                                              |
      | effort            | 9                 | 8                   | 7                 | 6                 | 5                 | 9                                                              |
      | model             |                   |                     |                   |                   | spark             | spark                                                          |
      | model             |                   | parrot              |                   |                   | spark             | parrot                                                         |
      | model             | swift             | parrot              |                   |                   | spark             | swift                                                          |
      | crew              |                   |                     |                   |                   |                   | main                                                           |
      | crew              |                   |                     |                   |                   | alice             | alice                                                          |
      | crew              | bob               |                     |                   |                   | alice             | bob                                                            |
      | cwd               |                   |                     |                   |                   |                   | /test/.isaac/crew/main                                         |
      | cwd               | /tmp/work         |                     |                   |                   |                   | /tmp/work                                                      |
      | context-mode      |                   |                     |                   |                   |                   | :full                                                          |
      | context-mode      |                   |                     |                   |                   | :reset            | :reset                                                         |
      | context-mode      |                   | :reset              |                   |                   | :full             | :reset                                                         |
      | context-mode      | :full             | :reset              |                   |                   | :reset            | :full                                                          |
      | compaction        |                   |                     |                   |                   |                   | {:strategy :rubberband :threshold 26214}                       |
      | compaction        |                   |                     |                   |                   | {:threshold 1000} | {:strategy :rubberband :threshold 1000}                        |
      | compaction        |                   |                     |                   | {:threshold 2000} | {:threshold 1000} | {:strategy :rubberband :threshold 2000}                        |
      | compaction        |                   |                     | {:threshold 3000} | {:threshold 2000} | {:threshold 1000} | {:strategy :rubberband :threshold 3000}                        |
      | compaction        |                   | {:threshold 4000}   | {:threshold 3000} | {:threshold 2000} | {:threshold 1000} | {:strategy :rubberband :threshold 4000}                        |
      | compaction        | {:threshold 5000} | {:threshold 4000}   | {:threshold 3000} | {:threshold 2000} | {:threshold 1000} | {:strategy :rubberband :threshold 5000}                        |
      | compaction        |                   | {:strategy :slinky} |                   |                   | {:threshold 1000} | {:strategy :slinky :threshold 26214 :head 9830 :async? false} |

  Scenario Outline: Runtime resolution for <field> on existing sessions
    Given a session "s" exists with <field> "<session>"
    And the EDN isaac file "isaac.edn" exists with:
      | path              | value      |
      | defaults.<field>  | <defaults> |
      | crew.main.<field> | <crew>     |
    When session behavior is resolved for "s"
    Then the resolved behavior for "s" matches:
      | key     | value      |
      | <field> | <expected> |

    Examples:
      | field             | session | crew    | defaults | expected |
      | history-retention | :retain | :prune  | :prune   | :retain  |
      | history-retention | :prune  | :retain | :retain  | :prune   |
      | effort            | 9       | 8       | 5        | 9        |
      | effort            |         | 8       | 5        | 8        |
      | effort            |         |         | 5        | 5        |
      | effort            |         |         |          | 7        |
