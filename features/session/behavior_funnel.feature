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

  Scenario Outline: Resolution after config change for <field>
    Given the EDN isaac file "isaac.edn" exists with:
      | path             | value     |
      | defaults.<field> | <initial> |
    And the following sessions exist:
      | name |
      | s    |
    When the EDN isaac file "isaac.edn" exists with:
      | path             | value     |
      | defaults.<field> | <changed> |
    Then the resolved behavior for "s" matches:
      | key     | value      |
      | <field> | <expected> |

    # State-defining fields (rows 1-3) lock at creation: expected = initial.
    # Behavioral fields (rows 4-8) re-cascade: expected = changed.
    Examples:
      | field             | initial           | changed           | expected                                |
      | history-retention | :retain           | :prune            | :retain                                 |
      | history-retention | :prune            | :retain           | :prune                                  |
      | crew              | alice             | bob               | alice                                   |
      | effort            | 5                 | 9                 | 9                                       |
      | effort            | 9                 | 5                 | 5                                       |
      | model             | spark             | parrot            | parrot                                  |
      | context-mode      | :full             | :reset            | :reset                                  |
      | compaction        | {:threshold 1000} | {:threshold 2000} | {:strategy :rubberband :threshold 2000} |

  Scenario: :cwd is locked to a session even when the crew default changes
    Given the EDN isaac file "isaac.edn" exists with:
      | path         | value |
      | defaults.crew | alice |
    And the following sessions exist:
      | name |
      | s    |
    # session 's' locks cwd to /test/.isaac/crew/alice
    When the EDN isaac file "isaac.edn" exists with:
      | path         | value |
      | defaults.crew | bob   |
    # new sessions would now lock to /test/.isaac/crew/bob,
    # but 's' keeps the cwd it locked at creation
    Then the resolved behavior for "s" matches:
      | key | value                   |
      | cwd | /test/.isaac/crew/alice |
