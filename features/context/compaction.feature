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
      | local | test-model | grover   | 60            |
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

  # Uses a stale large-window token total with a smaller current model window.
  Scenario: Switching to a smaller-context model runs compaction repeatedly until chat can continue
    Given the following sessions exist:
      | key                         | totalTokens |
      | agent:main:cli:direct:user1 | 200         |
    And the following models exist:
      | alias       | model             | provider | contextWindow |
      | claude-long | claude-opus-4-6   | grover   | 96            |
      | qwen3-coder | qwen3-coder:30b   | grover   | 32            |
    And the following agents exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |
    And the following messages are appended:
      | role      | content                                                                                                                                                                          |
      | user      | Earlier planning notes from the large-window model.                                                                                                                               |
      | assistant | Earlier planning summary from the large-window model.                                                                                                                             |
      | user      | Recent facts: release tracking, migration status, provider changes, logging updates, tool execution details, deployment concerns, rollback notes, and monitoring issues remain active after the downgrade. |
      | assistant | Recent answer: release tracking, migration status, provider changes, logging updates, tool execution details, deployment concerns, rollback notes, and monitoring issues remain important after the downgrade. |
    And the following model responses are queued:
      | type | content                     | model           |
      | text | Summary from first compact  | qwen3-coder:30b |
      | text | Summary from second compact | qwen3-coder:30b |
      | text | Final response after shrink | qwen3-coder:30b |
    When the user sends "Continue after model switch"
    Then the session listing has entries matching:
      | key                         | compactionCount |
      | agent:main:cli:direct:user1 | 2               |
    And the transcript has entries matching:
      | type       | summary                     |
      | compaction | Summary from first compact  |
      | compaction | Summary from second compact |
    And the transcript has entries matching:
      | type    | message.role | message.content              |
      | message | assistant    | Final response after shrink  |

  # Verifies that compaction resets token accumulators, not just the total.
  # Without the fix, stale inputTokens/outputTokens cause totalTokens to
  # rebound above threshold on the very next update-tokens! call.
  @wip
  Scenario: Successful compaction does not immediately re-trigger on the next user turn
    Given the following sessions exist:
      | key                         | inputTokens | outputTokens | totalTokens |
      | agent:main:cli:direct:user1 | 120         | 30           | 150         |
    And the following messages are appended:
      | role      | content                                                  |
      | user      | Please summarize our previous context before we continue. |
      | assistant | We covered tools, logs, and pending compaction fixes.     |
    And the following model responses are queued:
      | type | content                          | model      |
      | text | Summary after compaction         | test-model |
      | text | First reply after compaction     | test-model |
      | text | Second reply without re-compacts | test-model |
    When the user sends "Hello?"
    And the user sends "You there?"
    Then the session listing has entries matching:
      | key                         | compactionCount |
      | agent:main:cli:direct:user1 | 1               |
    And the transcript has entries matching:
      | type       | message.role | message.content                  | summary                  |
      | compaction |              |                                  | Summary after compaction |
      | message    | user         | Hello?                           |                          |
      | message    | assistant    | First reply after compaction     |                          |
      | message    | user         | You there?                       |                          |
      | message    | assistant    | Second reply without re-compacts |                          |
