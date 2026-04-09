Feature: Context Management
  Isaac tracks token usage and compacts conversation history
  when approaching the model's context window limit.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 100           |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  # --- Token Tracking ---

  Scenario: Token usage is tracked per session
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following model responses are queued:
      | type | content               | model |
      | text | Here is my response   | echo  |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then agent "main" has sessions matching:
      | key                         | inputTokens | outputTokens | totalTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       | #"\d+"      |

  # --- Compaction Trigger ---

  Scenario: Compaction triggers at 90% context usage
    Given agent "main" has sessions:
      | key                         | totalTokens | #comment                    |
      | agent:main:cli:direct:user1 | 95          | exceeds 90% of 100 window   |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content               |
      | message | user         | Please summarize our work     |
      | message | assistant    | We discussed logging and tools |
    And the following model responses are queued:
      | type | content                | model |
      | text | Summary of prior chat  | echo  |
      | text | Here is my answer      | echo  |
    When the user sends "What was decided?" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type       |
      | compaction |

  # --- Compaction Process ---

  Scenario: Conversation is compacted into a summary
    Given agent "main" has sessions:
      | key                         | totalTokens | #comment                    |
      | agent:main:cli:direct:user1 | 95          | exceeds 90% of 100 window   |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content              |
      | message | user         | What is Clojure?             |
      | message | assistant    | A functional Lisp on JVM... |
      | message | user         | What about Babashka?         |
      | message | assistant    | A fast Clojure scripting... |
    And the following model responses are queued:
      | type | content                | model |
      | text | Summary of prior chat  | echo  |
      | text | Here is my answer      | echo  |
    When the user sends "Continue" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type       | summary   |
      | compaction | #".{10,}" |
    And agent "main" has sessions matching:
      | key                         | compactionCount |
      | agent:main:cli:direct:user1 | 1               |

  # --- Tool Result Truncation ---

  Scenario: Large tool results are truncated in prompts
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type       | message.role | message.content                                                                                                                                                                                            |
      | message    | user         | Read the big file                                                                                                                                                                                           |
      | toolCall   |              |                                                                                                                                                                                                             |
      | toolResult |              | AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ |
    Then the prompt "What does it say?" on session "agent:main:cli:direct:user1" matches:
      | key                 | value                    |
      | messages[1].content | #"AAAA.*truncated.*ZZZZ" |
