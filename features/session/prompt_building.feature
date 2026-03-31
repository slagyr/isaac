@wip
Feature: Prompt Building
  The prompt builder composes an API request from the agent's soul
  (system prompt), conversation history, and tool definitions.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias       | model           | provider | contextWindow |
      | qwen3-coder | qwen3-coder:30b | ollama   | 32768         |
    And the following agents exist:
      | name | soul                                | model       |
      | main | You are Isaac, a helpful assistant.  | qwen3-coder |

  # --- Basic Composition ---

  Scenario: Build a prompt with soul and history
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content      |
      | user      | Knock knock  |
      | assistant | Who's there? |
      | user      | Cache        |
    When a prompt is built for the session
    Then the prompt matches:
      | key                 | value                               |
      | model               | qwen3-coder:30b                     |
      | messages[0].role    | system                              |
      | messages[0].content | You are Isaac, a helpful assistant.  |
      | messages[1].role    | user                                |
      | messages[1].content | Knock knock                         |
      | messages[2].role    | assistant                           |
      | messages[2].content | Who's there?                        |
      | messages[3].role    | user                                |
      | messages[3].content | Cache                               |

  Scenario: Build a prompt with tool definitions
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following agents exist:
      | name | soul               | model       |
      | main | You are Isaac.     | qwen3-coder |
    And the agent has tools:
      | name      | description              | parameters             |
      | read_file | Read a file's contents   | {"path": "string"}     |
      | exec      | Execute a shell command  | {"command": "string"}  |
    And the following messages are appended:
      | role | content         |
      | user | Read the README |
    When a prompt is built for the session
    Then the prompt matches:
      | key                               | value                    |
      | tools[0].function.name            | read_file                |
      | tools[0].function.description     | Read a file's contents   |
      | tools[1].function.name            | exec                     |
      | tools[1].function.description     | Execute a shell command  |

  # --- History After Compaction ---

  Scenario: Build a prompt after compaction
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content      |
      | user      | Knock knock  |
      | assistant | Who's there? |
      | user      | Cache        |
      | assistant | Cache who?   |
    And the session has been compacted with summary "User told a knock-knock joke about caching."
    And the following messages are appended:
      | role | content         |
      | user | Tell me another |
    When a prompt is built for the session
    Then the prompt matches:
      | key                 | value                                        |
      | messages[0].role    | system                                       |
      | messages[0].content | You are Isaac, a helpful assistant.           |
      | messages[1].role    | user                                         |
      | messages[1].content | User told a knock-knock joke about caching.  |
      | messages[2].role    | user                                         |
      | messages[2].content | Tell me another                              |

  # --- Token Awareness ---

  Scenario: Prompt reports token estimate
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When a prompt is built for the session
    Then the prompt has a token estimate greater than 0
