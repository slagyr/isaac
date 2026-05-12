Feature: Compaction Strategies
  Sessions define a compaction strategy that controls when and how
  transcript history is summarized. :rubberband is the default —
  fold everything when the window fills. :slinky folds the tail
  (oldest portion) and preserves a recent head intact, sized by the
  :head config (defaults to 30% of the context window).

  Background:
    Given an in-memory Isaac state directory "target/test-state"

  Scenario: default compaction parameters derive from context window
    Then the compaction defaults are:
      | context-window | threshold | head   |
      | 100           | 80        | 30     |
      | 8192          | 6553      | 2457   |
      | 32768         | 26214     | 9830   |
      | 65536         | 52428     | 19660  |
      | 128000        | 102400    | 38400  |
      | 200000        | 160000    | 60000  |
      | 272000        | 222000    | 81600  |
      | 1048576       | 998576    | 314572 |

  Scenario: rubberband compacts entire transcript when threshold exceeded
    Given the isaac EDN file "config/models/local.edn" exists with:
      | path | value |
      | model | test-model |
      | provider | grover |
      | context-window | 100 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | local |
      | soul | You are Isaac. |
    And the following sessions exist:
      | name    | total-tokens |
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

  Scenario: slinky folds the tail and preserves the head
    Given the isaac EDN file "config/models/local.edn" exists with:
      | path | value |
      | model | test-model |
      | provider | grover |
      | context-window | 200 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | local |
      | soul | You are Isaac. |
    And the following sessions exist:
      | name        | total-tokens | compaction.strategy | compaction.threshold | compaction.head |
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
