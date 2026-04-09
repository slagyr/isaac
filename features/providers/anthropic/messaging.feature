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

  # --- Request Format ---

  @wip
  Scenario: System prompt is a separate field
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When a prompt is built for the Anthropic provider
    Then the prompt matches:
      | key                 | value             |
      | model               | claude-sonnet-4-6 |
      | system[0].type      | text              |
      | system[0].text      | You are Isaac.    |
      | messages[0].role    | user              |
      | messages[0].content | Hello             |

  @wip
  Scenario: Prompt caching breakpoints are applied
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Knock knock     |
      | message | assistant    | Who's there?    |
      | message | user         | Cache           |
    When a prompt is built for the Anthropic provider
    Then the prompt matches:
      | key                          | value     |
      | system[0].cache_control.type | ephemeral |
    And the penultimate user message has cache_control

  # --- Response Handling ---

  @wip
  Scenario: Parse a response into a transcript entry
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | What is 2+2?    |
    When the user sends "What is 2+2?" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.model     | message.provider |
      | message | assistant    | claude-sonnet-4-6 | anthropic        |
    And agent "main" has sessions matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  @wip
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

  @wip
  Scenario: Streaming response via SSE
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Tell me a story |
    When the user sends "Tell me a story" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role |
      | message | assistant    |

  # --- Tool Calling ---

  @wip
  Scenario: Tool call with Anthropic format
    Given the agent has tools:
      | name      | description            | parameters         |
      | read_file | Read a file's contents | {"path": "string"} |
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

  @wip
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
