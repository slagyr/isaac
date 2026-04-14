@wip
Feature: Context Management
  Isaac tracks token usage and compacts conversation history
  when approaching the model's context window limit.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 100           |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  # --- Token Tracking ---

  Scenario: Token usage is tracked per session
    Given the following sessions exist:
      | name          |
      | context-track |
    And the following model responses are queued:
      | type | content               | model |
      | text | Here is my response   | echo  |
    When the user sends "Hello" on session "context-track"
    Then the following sessions match:
      | id            | inputTokens | outputTokens | totalTokens |
      | context-track | #"\d+"      | #"\d+"       | #"\d+"      |

  # --- Compaction Trigger ---

  Scenario: Compaction triggers at 90% context usage
    Given the following sessions exist:
      | name            | totalTokens | #comment                    |
      | context-compact | 95          | exceeds 90% of 100 window   |
    And session "context-compact" has transcript:
      | type    | message.role | message.content               |
      | message | user         | Please summarize our work     |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type | content                | model |
      | text | Summary of prior chat  | echo  |
      | text | Here is my answer      | echo  |
    When the user sends "What was decided?" on session "context-compact"
    Then session "context-compact" has transcript matching:
      | type       |
      | compaction |

  # --- Compaction Process ---

  Scenario: Conversation is compacted into a summary
    Given the following sessions exist:
      | name            | totalTokens | #comment                    |
      | context-summary | 95          | exceeds 90% of 100 window   |
    And session "context-summary" has transcript:
      | type    | message.role | message.content              |
      | message | user         | What is Clojure?             |
      | message | assistant    | A functional Lisp on JVM... |
      | message | user         | What about Babashka?         |
      | message | assistant    | A fast Clojure scripting... |
    And the following model responses are queued:
      | type | content                | model |
      | text | Summary of prior chat  | echo  |
      | text | Here is my answer      | echo  |
    When the user sends "Continue" on session "context-summary"
    Then session "context-summary" has transcript matching:
      | type       | summary   |
      | compaction | #".{10,}" |
    And the following sessions match:
      | id              | compactionCount |
      | context-summary | 1               |

  # --- Tool Result Truncation ---

  Scenario: Large tool results are truncated in prompts
    Given the following sessions exist:
      | name             |
      | context-truncate |
    And session "context-truncate" has transcript:
      | type       | message.role | message.content                                                                                                                                                                                            |
      | message    | user         | Read the big file                                                                                                                                                                                           |
      | toolCall   |              |                                                                                                                                                                                                             |
      | toolResult |              | AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ |
    Then the prompt "What does it say?" on session "context-truncate" matches:
      | key                 | value                    |
      | messages[1].content | #"AAAA.*truncated.*ZZZZ" |
