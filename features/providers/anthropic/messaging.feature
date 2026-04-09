Feature: Anthropic Messaging
  Isaac composes requests for and handles responses from the
  Anthropic Messages API, including prompt caching and tool calling.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | claude |
    And the provider "anthropic" is configured with:
      | key     | value                     |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |

  # --- Request Format ---

  Scenario: System prompt is a separate field
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    Then the prompt "Hello" on session "agent:main:cli:direct:user1" matches:
      | key                 | value             |
      | model               | claude-sonnet-4-6 |
      | system[0].type      | text              |
      | system[0].text      | You are Isaac.    |
      | messages[0].role    | user              |
      | messages[0].content | Hello             |

  Scenario: Prompt caching breakpoints are applied
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Knock knock     |
      | message | assistant    | Who's there?    |
    Then the prompt "Cache" on session "agent:main:cli:direct:user1" matches:
      | key                                       | value       |
      | system[0].cache_control.type              | ephemeral   |
      | messages[0].content[0].text               | Knock knock |
      | messages[0].content[0].cache_control.type | ephemeral   |

  # --- Response Handling ---

  @slow
  Scenario: Cache token usage is tracked
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
      | message | assistant    | Hi there        |
      | message | user         | Hello again     |
    When the user sends "Hello again" on session "agent:main:cli:direct:user1"
    Then agent "main" has sessions matching:
      | key                         | cacheRead | cacheWrite |
      | agent:main:cli:direct:user1 | #"\d+"    | #"\d+"     |

  # --- Tool Calling ---

  @slow
  Scenario: Tool call with Anthropic format
    Given the agent has tools:
      | name      | description            | parameters         |
      | read_file | Read a file's contents | {"type":"object","properties":{"path":{"type":"string"}},"required":["path"]} |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Read the README |
    When the user sends "Read the README" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.content[0].type |
      | message | assistant    | toolCall                |
    And session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role |
      | message | toolResult   |

  # --- Error Handling ---

  Scenario: Server unreachable
    Given the provider "anthropic" is configured with:
      | key     | value                  |
      | baseUrl | http://localhost:99999 |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                  |
      | :error | :chat/response-failed  |
