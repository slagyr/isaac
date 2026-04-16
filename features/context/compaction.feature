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
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |

  Scenario: Chat logs the compaction trigger with provider and model context
    Given the following sessions exist:
      | name            | totalTokens | #comment                  |
      | compaction-chat | 95          | exceeds 90% of 100 window |
    And session "compaction-chat" has transcript:
      | type    | message.role | message.content                |
      | message | user         | Please summarize our work      |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
    When the user sends "Can you summarize README.md?" on session "compaction-chat"
    Then the log has entries matching:
      | level  | event                       | session         | provider | model      | totalTokens | contextWindow |
      | :debug | :session/compaction-check   | compaction-chat | grover   | test-model | 95          | 100           |
      | :info  | :session/compaction-started | compaction-chat | grover   | test-model | 95          | 100           |

  Scenario: The new user message is preserved after compaction
    Given the following sessions exist:
      | name            | totalTokens | #comment                  |
      | compaction-chat | 95          | exceeds 90% of 100 window |
    And session "compaction-chat" has transcript:
      | type    | message.role | message.content                |
      | message | user         | Please summarize our work      |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
    When the user sends "Can you summarize README.md?" on session "compaction-chat"
    Then session "compaction-chat" has transcript matching:
      | #index | type       | summary               | message.role | message.content              |
      | 1      | compaction | Summary of prior chat |              |                              |
      | 2      | message    |                       | user         | Can you summarize README.md? |

  Scenario: Chat completes after compaction
    Given the following sessions exist:
      | name            | totalTokens | #comment                  |
      | compaction-chat | 95          | exceeds 90% of 100 window |
    And session "compaction-chat" has transcript:
      | type    | message.role | message.content                |
      | message | user         | Please summarize our work      |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type | content               | model      |
      | text | Summary of prior chat | test-model |
      | text | README summary        | test-model |
    When the user sends "Can you summarize README.md?" on session "compaction-chat"
    Then session "compaction-chat" has transcript matching:
      | type    | message.role | message.content |
      | message | assistant    | README summary  |

  Scenario: Compaction failure is logged and chat proceeds without looping
    Given the following sessions exist:
      | name         | totalTokens | #comment                  |
      | failure-chat | 95          | exceeds 90% of 100 window |
    And session "failure-chat" has transcript:
      | type    | message.role | message.content                |
      | message | user         | Please summarize our work      |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type  | content                 | model      |
      | error | context length exceeded | test-model |
      | text  | Here is my answer       | test-model |
    When the user sends "What was decided?" on session "failure-chat"
    Then the log has entries matching:
      | level  | event                      | session      |
      | :error | :session/compaction-failed | failure-chat |
    And session "failure-chat" has transcript matching:
      | type    | message.role | message.content   |
      | message | assistant    | Here is my answer |

  Scenario: Compaction targets only the oldest messages when history exceeds the model context window
    Given the following sessions exist:
      | name            | totalTokens | compaction.strategy | compaction.threshold | compaction.tail | #comment                  |
      | partial-compact | 95          | slinky              | 90                   | 35              | exceeds threshold         |
    And the following models exist:
      | alias | model      | provider | contextWindow |
      | local | test-model | grover   | 60            |
    And session "partial-compact" has transcript:
      | type    | message.role | message.content                                       | tokens |
      | message | user         | First question about the project status               | 20     |
      | message | assistant    | The project status is healthy and on track            | 20     |
      | message | user         | Second question about the upcoming release            | 20     |
      | message | assistant    | The release is scheduled for the end of month         | 20     |
    And the following model responses are queued:
      | type | content                   | model      |
      | text | Summary of first exchange | test-model |
      | text | Third answer              | test-model |
    When the user sends "Third question" on session "partial-compact"
    Then session "partial-compact" has transcript matching:
      | #index | type       | message.role | message.content                               | summary                   |
      | 1      | message    | user         | Second question about the upcoming release    |                           |
      | 2      | message    | assistant    | The release is scheduled for the end of month |                           |
      | 3      | compaction |              |                                               | Summary of first exchange |
      | 4      | message    | user         | Third question                                |                           |
      | 5      | message    | assistant    | Third answer                                  |                           |

  Scenario: Switching to a smaller-context model runs compaction repeatedly until chat can continue
    Given the following sessions exist:
      | name          | totalTokens | #comment                             |
      | model-switch  | 200         | accumulated under large-window model |
    And the following models exist:
      | alias       | model             | provider | contextWindow |
      | claude-long | claude-opus-4-6   | grover   | 96            |
      | qwen3-coder | qwen3-coder:30b   | grover   | 32            |
    And the following crew exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |
    And session "model-switch" has transcript:
      | type    | message.role | message.content                                                                                                                                                                         |
      | message | user         | Earlier planning notes from the large-window model.                                                                                                                                      |
      | message | assistant    | Earlier planning summary from the large-window model.                                                                                                                                    |
      | message | user         | Recent facts: release tracking, migration status, provider changes, logging updates, tool execution details, deployment concerns, rollback notes, and monitoring issues remain active after the downgrade. |
      | message | assistant    | Recent answer: release tracking, migration status, provider changes, logging updates, tool execution details, deployment concerns, rollback notes, and monitoring issues remain important after the downgrade. |
    And the following model responses are queued:
      | type | content                     | model           |
      | text | Summary from first compact  | qwen3-coder:30b |
      | text | Summary from second compact | qwen3-coder:30b |
      | text | Final response after shrink | qwen3-coder:30b |
    When the user sends "Continue after model switch" on session "model-switch"
    Then the following sessions match:
      | id           | compactionCount |
      | model-switch | 2               |
    And session "model-switch" has transcript matching:
      | type       | summary                     |
      | compaction | Summary from first compact  |
      | compaction | Summary from second compact |
    And session "model-switch" has transcript matching:
      | type    | message.role | message.content              |
      | message | assistant    | Final response after shrink  |

  Scenario: Successful compaction does not immediately re-trigger on the next user turn
    Given the following sessions exist:
      | name          | inputTokens | outputTokens | totalTokens | #comment                          |
      | rebound-test  | 120         | 30           | 150         | stale accumulators cause rebound  |
    And session "rebound-test" has transcript:
      | type    | message.role | message.content                                                  |
      | message | user         | Please summarize our previous context before we continue. |
      | message | assistant    | We covered tools, logs, and pending compaction fixes.     |
    And the following model responses are queued:
      | type | content                          | model      |
      | text | Summary after compaction         | test-model |
      | text | First reply after compaction     | test-model |
      | text | Second reply without re-compacts | test-model |
    When the user sends "Hello?" on session "rebound-test"
    And the user sends "You there?" on session "rebound-test"
    Then the following sessions match:
      | id           | compactionCount |
      | rebound-test | 1               |
    And session "rebound-test" has transcript matching:
      | type       | message.role | message.content                  | summary                  |
      | compaction |              |                                  | Summary after compaction |
      | message    | user         | Hello?                           |                          |
      | message    | assistant    | First reply after compaction     |                          |
      | message    | user         | You there?                       |                          |
      | message    | assistant    | Second reply without re-compacts |                          |
