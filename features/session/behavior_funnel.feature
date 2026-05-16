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
      | field             | creator | crew    | model   | provider | defaults | expected |
      | history-retention |         |         |         |          |          | :retain  |
      | history-retention |         |         |         |          | :prune   | :prune   |
      | history-retention |         |         |         | :retain  | :prune   | :retain  |
      | history-retention |         |         | :prune  | :retain  | :retain  | :prune   |
      | history-retention |         | :retain | :prune  | :prune   | :prune   | :retain  |
      | history-retention | :prune  | :retain | :retain | :retain  | :retain  | :prune   |
      | effort            |         |         |         |          |          | 7        |
      | effort            |         |         |         |          | 5        | 5        |
      | effort            |         |         |         | 6        | 5        | 6        |
      | effort            |         |         | 7       | 6        | 5        | 7        |
      | effort            |         | 8       | 7       | 6        | 5        | 8        |
      | effort            | 9       | 8       | 7       | 6        | 5        | 9        |

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
