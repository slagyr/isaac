@wip
Feature: Context Compaction Logging
  Isaac logs why context compaction was triggered during normal chat flow
  and preserves the new user message after compaction.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |
    And the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 100           |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | local |

  Scenario: Chat logs the compaction trigger with provider and model context
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the following model responses are queued:
      | type | content                | model      |
      | text | Summary of prior chat  | test-model |
      | text | README summary         | test-model |
    When the user sends "Can you summarize README.md?"
    Then the log has entries matching:
      | level  | event                       | session                      | provider | model      | totalTokens | contextWindow |
      | :debug | :context/compaction-check   | agent:main:cli:direct:user1 | grover   | test-model | 95          | 100           |
      | :info  | :context/compaction-started | agent:main:cli:direct:user1 | grover   | test-model | 95          | 100           |

  Scenario: The new user message is preserved after compaction
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the following model responses are queued:
      | type | content                | model      |
      | text | Summary of prior chat  | test-model |
      | text | README summary         | test-model |
    When the user sends "Can you summarize README.md?"
    Then the transcript has entries matching:
      | #index | type       | summary               | message.role | message.content              |
      | 3      | compaction | Summary of prior chat |              |                              |
      | 4      | message    |                       | user         | Can you summarize README.md? |

  Scenario: Chat completes after compaction
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the following model responses are queued:
      | type | content                | model      |
      | text | Summary of prior chat  | test-model |
      | text | README summary         | test-model |
    When the user sends "Can you summarize README.md?"
    Then the transcript has entries matching:
      | type    | message.role | message.content |
      | message | assistant    | README summary  |
