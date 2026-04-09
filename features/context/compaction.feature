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
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
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
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
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
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
    When the user sends "Can you summarize README.md?"
    Then the transcript has entries matching:
      | type    | message.role | message.content |
      | message | assistant    | README summary  |

  Scenario: Compaction failure is logged and chat proceeds without looping
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following messages are appended:
      | role      | content                        |
      | user      | Please summarize our work      |
      | assistant | We discussed logging and tools |
    And the following model responses are queued:
      | type  | content                 | model      |
      | error | context length exceeded | test-model |
      | text  | Here is my answer       | test-model |
    When the user sends "What was decided?"
    Then the log has entries matching:
      | level  | event                      | session                     |
      | :error | :context/compaction-failed | agent:main:cli:direct:user1 |
    And the transcript has entries matching:
      | type    | message.role | message.content   |
      | message | assistant    | Here is my answer |

  Scenario: Compaction targets only the oldest messages when history exceeds the model context window
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 95          |
    And the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 20            |
    And the following messages are appended:
      | role      | content                                        |
      | user      | First question about the project status        |
      | assistant | The project status is healthy and on track     |
      | user      | Second question about the upcoming release     |
      | assistant | The release is scheduled for the end of month  |
    And the following model responses are queued:
      | type | content                   | model      |
      | text | Summary of first exchange | test-model |
      | text | Third answer              | test-model |
    When the user sends "Third question"
    Then the transcript has entries matching:
      | #index | type       | message.role | message.content                                | summary                   |
      | 1      | message    | user         | First question about the project status        |                           |
      | 2      | message    | assistant    | The project status is healthy and on track     |                           |
      | 3      | message    | user         | Second question about the upcoming release     |                           |
      | 4      | message    | assistant    | The release is scheduled for the end of month  |                           |
      | 5      | compaction |              |                                                | Summary of first exchange |
      | 6      | message    | user         | Third question                                 |                           |
      | 7      | message    | assistant    | Third answer                                   |                           |
