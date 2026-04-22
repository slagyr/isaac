Feature: Delivery queue
  Outbound comm posts that fail transiently go into a persistent
  retry queue instead of being dropped. Each delivery is a small EDN
  file under <state-dir>/delivery/. The worker attempts delivery on
  a schedule; successes are removed; failures after 5 attempts move
  to failed/ for manual review.

  Retry policy (fixed in v1): 5 attempts with exponential backoff
  at 1s, 5s, 30s, 2m, 10m.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And config:
      | comms.discord.token | test-token |

  @wip
  Scenario: a successful delivery is removed from the queue
    Given the EDN state file "delivery/pending/7f3a.edn" contains:
      | path    | value                           |
      | id      | 7f3a                            |
      | comm    | discord                         |
      | target  | C999                            |
      | content | Hello from the delivery worker. |
    And the URL "https://discord.com/api/v10/channels/C999/messages" responds with:
      | status | 200 |
    When the delivery worker ticks
    Then the EDN state file "delivery/pending/7f3a.edn" does not exist
    And an outbound HTTP request to "https://discord.com/api/v10/channels/C999/messages" matches:
      | method       | POST                            |
      | body.content | Hello from the delivery worker. |

  @wip
  Scenario: a transient failure reschedules the delivery with backoff
    Given the EDN state file "delivery/pending/7f3a.edn" contains:
      | path     | value             | #comment                          |
      | id       | 7f3a              |                                   |
      | comm     | discord           |                                   |
      | target   | C999              |                                   |
      | content  | Trying once more. |                                   |
      | attempts | 0                 | fresh delivery, no prior failures |
    And the URL "https://discord.com/api/v10/channels/C999/messages" responds with:
      | status | 500 |
    When the delivery worker ticks at "2026-04-21T10:00:00Z"
    Then the EDN state file "delivery/pending/7f3a.edn" contains:
      | path            | value                | #comment                                  |
      | attempts        | 1                    | incremented from 0 after this failure     |
      | next-attempt-at | 2026-04-21T10:00:01Z | tick time + 1 second (first backoff step) |

  @wip
  Scenario: delivery moves to failed after max attempts
    Given the EDN state file "delivery/pending/7f3a.edn" contains:
      | path     | value           | #comment                                          |
      | id       | 7f3a            |                                                   |
      | comm     | discord         |                                                   |
      | target   | C999            |                                                   |
      | content  | Goodbye, world. |                                                   |
      | attempts | 4               | one short of the 5-attempt max; this tick is last |
    And the URL "https://discord.com/api/v10/channels/C999/messages" responds with:
      | status | 500 |
    When the delivery worker ticks at "2026-04-21T10:00:00Z"
    Then the EDN state file "delivery/pending/7f3a.edn" does not exist
    And the EDN state file "delivery/failed/7f3a.edn" contains:
      | path     | value | #comment                                |
      | attempts | 5     | hit the max on this tick; dead-lettered |
      | id       | 7f3a  |                                         |
    And the log has entries matching:
      | level | event                   | id   |
      | error | :delivery/dead-lettered | 7f3a |
