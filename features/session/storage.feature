Feature: Session Storage
  Isaac persists conversation sessions as JSONL transcript files
  with a JSON index, compatible with OpenClaw's storage format.

  Background:
    Given an empty Isaac state directory "target/test-state"

  # --- Session Lifecycle ---

  Scenario: Create a new session
    When sessions are created for agent "main":
      | key                         |
      | agent:main:cli:direct:user1 |
    Then agent "main" has 1 session
    And agent "main" has sessions matching:
      | key                         | sessionFile  | channel | chatType | compactionCount | inputTokens | outputTokens | totalTokens |
      | agent:main:cli:direct:user1 | #".+\.jsonl" | cli     | direct   | 0               | 0           | 0            | 0           |
    And session "agent:main:cli:direct:user1" has 1 transcript entry
    And session "agent:main:cli:direct:user1" has transcript matching:
      | type    | id              | timestamp                               |
      | session | #"[a-f0-9]{8}" | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |

  Scenario: List sessions
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
      | agent:main:cli:direct:user2 |
    Then agent "main" has 2 sessions
    And agent "main" has sessions matching:
      | key                         | updatedAt                               |
      | agent:main:cli:direct:user1 | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |
      | agent:main:cli:direct:user2 | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |

  Scenario: Creating a session for an existing key resumes it
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When sessions are created for agent "main":
      | key                         |
      | agent:main:cli:direct:user1 |
    Then agent "main" has 1 session
    And session "agent:main:cli:direct:user1" has 2 transcript entries

  Scenario: Resume an existing session
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
      | message | assistant    | Hi there        |
      | message | user         | How are you?    |
    Then session "agent:main:cli:direct:user1" has 4 transcript entries
    And agent "main" has 1 session

  # --- Message Entries ---

  Scenario: Append a user message
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
    Then session "agent:main:cli:direct:user1" has 2 transcript entries
    And session "agent:main:cli:direct:user1" has transcript matching:
      | #index | type    | message.role | message.content |
      | 1      | message | user         | Hello           |

  Scenario: Append an assistant message
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content | message.model | message.provider |
      | message | assistant    | Hi there        | qwen3-coder   | ollama           |
    Then session "agent:main:cli:direct:user1" has 3 transcript entries
    And session "agent:main:cli:direct:user1" has transcript matching:
      | #index | type    | message.role | message.content | message.model | message.provider |
      | 2      | message | assistant    | Hi there        | qwen3-coder   | ollama           |

  Scenario: Append a tool call and result
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Read the README |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type       | name      | id       | arguments          | message.content   | isError |
      | toolCall   | read_file | call_123 | {"path": "README"} |                   |         |
      | toolResult |           | call_123 |                    | # Isaac\nA CLI... | false   |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.content[0].type | message.content[0].name |
      | message | assistant    | toolCall                | read_file               |
    And session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.toolCallId |
      | message | toolResult   | call_123           |

  # --- Entry Linking ---

  Scenario: Entries form a linked chain via parentId
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
      | message | assistant    | Hi there        |
      | message | user         | How are you?    |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | id                     | parentId |
      | 0      | #"[a-f0-9]{8}":header  |          |
      | 1      | #"[a-f0-9]{8}":msg1    | #header  |
      | 2      | #"[a-f0-9]{8}":msg2    | #msg1    |
      | 3      | #"[a-f0-9]{8}":msg3    | #msg2    |

  # --- Index Updates ---

  Scenario: Index is updated on each append
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
    Then agent "main" has sessions matching:
      | key                         | updatedAt                               |
      | agent:main:cli:direct:user1 | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |

  # --- OpenClaw Format Compatibility ---

  Scenario: Session header includes version and working directory
    When sessions are created for agent "main":
      | key                         |
      | agent:main:cli:direct:user1 |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | type    | version | cwd   |
      | 0      | session | 3       | #".+" |

  Scenario: Entry IDs are 8-character hex strings
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | id             |
      | 0      | #"[a-f0-9]{8}" |
      | 1      | #"[a-f0-9]{8}" |

  Scenario: Timestamps use ISO 8601 format
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | timestamp                               |
      | 0      | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |
      | 1      | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |
    And agent "main" has sessions matching:
      | key                         | updatedAt                               |
      | agent:main:cli:direct:user1 | #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}" |

  Scenario: Index is keyed by session key
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
      | agent:main:cli:direct:user2 |
    Then the session index for agent "main" has keys:
      | key                         |
      | agent:main:cli:direct:user1 |
      | agent:main:cli:direct:user2 |

  Scenario: Message content stored as block arrays
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content |
      | message | user         | Hello           |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | message.content[0].type | message.content[0].text |
      | 1      | text                    | Hello                   |

  Scenario: Assistant messages include per-turn usage metadata
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When entries are appended to session "agent:main:cli:direct:user1":
      | type    | message.role | message.content | message.model | message.provider | message.api | message.usage.input | message.usage.output | message.stopReason |
      | message | assistant    | Hi there        | qwen3-coder   | ollama           | ollama      | 100                 | 25                   | stop               |
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.usage.input | message.usage.output | message.stopReason | message.api |
      | message | assistant    | 100                 | 25                   | stop               | ollama      |
