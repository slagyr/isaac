Feature: Context Management
  Isaac tracks token usage and compacts conversation history
  when approaching the model's context window limit.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias       | model           | provider | contextWindow |
      | qwen3-coder | qwen3-coder:30b | ollama   | 32768         |
    And the following agents exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |

  # --- Token Tracking ---

  Scenario: Token usage is tracked per session
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And 5 exchanges have been completed
    Then the session listing has entries matching:
      | key                         | inputTokens | outputTokens | totalTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       | #"\d+"      |

  # --- Compaction Trigger ---

  Scenario: Compaction triggers at 90% context usage
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the session totalTokens exceeds 90% of the context window
    When the next user message is sent
    Then compaction is triggered before sending the prompt
    And the transcript has entries matching:
      | type       |
      | compaction |

  # --- Compaction Process ---

  Scenario: Conversation is compacted into a summary
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content                     |
      | user      | What is Clojure?            |
      | assistant | A functional Lisp on JVM... |
      | user      | What about Babashka?        |
      | assistant | A fast Clojure scripting... |
    When compaction is triggered
    Then the transcript has entries matching:
      | type       | summary   | firstKeptEntryId | tokensBefore |
      | compaction | #".{10,}" | #".{36}"         | #"\d+"       |
    And the session listing has entries matching:
      | key                         | compactionCount |
      | agent:main:cli:direct:user1 | 1               |

  # --- Tool Result Truncation ---

  Scenario: Large tool results are truncated in prompts
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the session contains a tool result of 50000 characters
    When a prompt is built for the session
    Then the tool result in the prompt is less than 50000 characters
    And the tool result preserves content at the start and end
