Feature: Compaction Strategies
  Sessions define a compaction strategy that controls when and how
  transcript history is summarized. :rubberband is the default —
  compact everything when the window fills. :slinky compacts only
  the oldest portion, preserving recent context intact.

  Background:
    Given an in-memory Isaac state directory "target/test-state"

  Scenario: default compaction parameters derive from context window
    Then the compaction defaults are:
      | contextWindow | threshold | tail   |
      | 100           | 80        | 70     |
      | 8192          | 6553      | 5734   |
      | 32768         | 26214     | 22937  |
      | 65536         | 52428     | 45875  |
      | 128000        | 102400    | 89600  |
      | 200000        | 160000    | 140000 |
      | 272000        | 222000    | 190400 |
      | 1048576       | 998576    | 898576 |

  Scenario: rubberband compacts entire transcript when threshold exceeded
    Given the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 100           |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |
    And the following sessions exist:
      | name    | totalTokens |
      | rb-test | 95          |
    And session "rb-test" has transcript:
      | type    | message.role | message.content            |
      | message | user         | Tell me about compaction   |
      | message | assistant    | It summarizes old messages |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | Here is my response   | test-model |
    When the user sends "hello" on session "rb-test"
    Then session "rb-test" has 4 transcript entries
    And session "rb-test" has transcript matching:
      | type       | message.role | message.content     | summary               |
      | compaction |              |                     | Summary of prior chat |
      | message    | user         | hello               |                       |
      | message    | assistant    | Here is my response |                       |

  Scenario: slinky compacts only the tail of the transcript
    Given the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 200           |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |
    And the following sessions exist:
      | name        | totalTokens | compaction.strategy | compaction.threshold | compaction.tail |
      | slinky-test | 170         | slinky              | 160                 | 80              |
    And session "slinky-test" has transcript:
      | type    | message.role | message.content  | tokens |
      | message | user         | old topic        | 40     |
      | message | assistant    | old reply        | 40     |
      | message | user         | recent topic     | 40     |
      | message | assistant    | recent reply     | 50     |
    And the following model responses are queued:
      | type | content        | model      |
      | text | Tail summary   | test-model |
      | text | Fresh response | test-model |
    When the user sends "hello" on session "slinky-test"
    Then session "slinky-test" has 6 transcript entries
    And session "slinky-test" has transcript matching:
      | type       | message.role | message.content | summary      |
      | compaction |              |                 | Tail summary |
      | message    | user         | recent topic    |              |
      | message    | assistant    | recent reply    |              |
      | message    | user         | hello           |              |
      | message    | assistant    | Fresh response  |              |
