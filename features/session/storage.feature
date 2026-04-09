Feature: Session Storage
  Isaac persists conversation sessions as JSONL transcript files
  with a JSON index, modeled after OpenClaw's storage format.

  Background:
    Given an empty Isaac state directory "target/test-state"

  # --- Session Lifecycle ---

  Scenario: Create a new session
    When the following sessions are created:
      | key                         |
      | agent:main:cli:direct:user1 |
    Then the session listing has 1 entry
    And the session listing has entries matching:
      | key                         | sessionFile  | channel | chatType | compactionCount | inputTokens | outputTokens | totalTokens |
      | agent:main:cli:direct:user1 | #".+\.jsonl" | cli     | direct   | 0               | 0           | 0            | 0           |
    And the transcript has 1 entry
    And the transcript has entries matching:
      | type    | id               | timestamp |
      | session | #"[a-f0-9-]{36}" | #"\d{13}" |

  Scenario: List sessions
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
      | agent:main:cli:direct:user2 |
    Then the session listing has 2 entries
    And the session listing has entries matching:
      | key                         | updatedAt |
      | agent:main:cli:direct:user1 | #"\d{13}" |
      | agent:main:cli:direct:user2 | #"\d{13}" |

  Scenario: Creating a session for an existing key resumes it
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the following sessions are created:
      | key                         |
      | agent:main:cli:direct:user1 |
    Then the session listing has 1 entry
    And the transcript has 2 entries

  Scenario: Resume an existing session
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content      |
      | user      | Hello        |
      | assistant | Hi there     |
      | user      | How are you? |
    When the session is loaded for key "agent:main:cli:direct:user1"
    Then the transcript has 4 entries
    And the session listing has 1 entry

  # --- Message Entries ---

  Scenario: Append a user message
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    When the following messages are appended:
      | role | content |
      | user | Hello   |
    Then the transcript has 2 entries
    And the transcript has entries matching:
      | #index | type    | message.role | message.content |
      | 1      | message | user         | Hello           |

  Scenario: Append an assistant message
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the following messages are appended:
      | role      | content  | model       | provider |
      | assistant | Hi there | qwen3-coder | ollama   |
    Then the transcript has 3 entries
    And the transcript has entries matching:
      | #index | type    | message.role | message.content | message.model | message.provider |
      | 2      | message | assistant    | Hi there        | qwen3-coder   | ollama           |

  Scenario: Append a tool call and result
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content         |
      | user | Read the README |
    When an assistant message with a tool call is appended:
      | tool_name | tool_id  | arguments          |
      | read_file | call_123 | {"path": "README"} |
    And a tool result is appended:
      | tool_id  | content           | isError |
      | call_123 | # Isaac\nA CLI... | false   |
    Then the transcript has entries matching:
      | type    | message.role | message.content[0].type | message.content[0].name |
      | message | assistant    | toolCall                | read_file               |
    And the transcript has entries matching:
      | type    | message.role | message.toolCallId |
      | message | toolResult   | call_123           |

  # --- Entry Linking ---

  Scenario: Entries form a linked chain via parentId
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    When the following messages are appended:
      | role      | content      |
      | user      | Hello        |
      | assistant | Hi there     |
      | user      | How are you? |
    Then the transcript has entries matching:
      | #index | id              | parentId |
      | 0      | #".{36}":header |          |
      | 1      | #".{36}":msg1   | #header  |
      | 2      | #".{36}":msg2   | #msg1    |
      | 3      | #".{36}":msg3   | #msg2    |

  # --- Index Updates ---

  Scenario: Index is updated on each append
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    When the following messages are appended:
      | role | content |
      | user | Hello   |
    Then the session listing has entries matching:
      | key                         | updatedAt |
      | agent:main:cli:direct:user1 | #"\d{13}" |

