Feature: Context Compaction Logging
  Isaac logs why context compaction was triggered during normal chat flow
  and preserves the new user message after compaction.

  Background:
    Given an empty Isaac state directory "target/test-state"
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
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the session totalTokens exceeds 90% of the context window
    And the following model responses are queued:
      | content               | model      |
      | Summary of prior chat | test-model |
      | README summary        | test-model |
    When the user sends "Can you summarize README.md?"
    Then the log has entries matching:
      | level  | event                    | session                      | provider | model      | totalTokens | contextWindow |
      | :debug | :chat/compaction-check   | agent:main:cli:direct:user1 | grover   | test-model | 95          | 100           |
      | :debug | :chat/compaction-started | agent:main:cli:direct:user1 | grover   | test-model | 95          | 100           |

  Scenario: The new user message is preserved after compaction
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the session totalTokens exceeds 90% of the context window
    And the following model responses are queued:
      | content               | model      |
      | Summary of prior chat | test-model |
      | README summary        | test-model |
    When the user sends "Can you summarize README.md?"
    Then the transcript has entries matching:
      | #index | type       | summary               | message.role | message.content              |
      | 3      | compaction | Summary of prior chat |              |                              |
      | 4      | message    |                       | user         | Can you summarize README.md? |

  Scenario: Chat completes after compaction
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the session totalTokens exceeds 90% of the context window
    And the following model responses are queued:
      | content               | model      |
      | Summary of prior chat | test-model |
      | README summary        | test-model |
    When the user sends "Can you summarize README.md?"
    Then the transcript has entries matching:
      | type    | message.role | message.content |
      | message | assistant    | README summary  |
