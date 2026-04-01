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
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
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
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content      |
      | user      | Knock knock  |
      | assistant | Who's there? |
      | user      | Cache        |
    When a prompt is built for the Anthropic provider
    Then the prompt matches:
      | key                          | value     |
      | system[0].cache_control.type | ephemeral |
    And the penultimate user message has cache_control

  # --- Response Handling ---

  @wip
  Scenario: Parse a response into a transcript entry
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content      |
      | user | What is 2+2? |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.model     | message.provider |
      | message | assistant    | claude-sonnet-4-6 | anthropic        |
    And the session listing has entries matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  @wip
  Scenario: Cache token usage is tracked
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role      | content     |
      | user      | Hello       |
      | assistant | Hi there    |
      | user      | Hello again |
    When the prompt is sent to the LLM
    Then the session listing has entries matching:
      | key                         | cacheRead | cacheWrite |
      | agent:main:cli:direct:user1 | #"\d+"    | #"\d+"     |

  @wip
  Scenario: Streaming response via SSE
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content         |
      | user | Tell me a story |
    When the prompt is streamed to the LLM
    Then response chunks arrive incrementally
    And the transcript has entries matching:
      | type    | message.role |
      | message | assistant    |

  # --- Tool Calling ---

  @wip
  Scenario: Tool call with Anthropic format
    Given the agent has tools:
      | name      | description            | parameters         |
      | read_file | Read a file's contents | {"path": "string"} |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content         |
      | user | Read the README |
    When the prompt is sent to the LLM
    And the model responds with a tool call
    Then the transcript has entries matching:
      | type    | message.role | message.content[0].type |
      | message | assistant    | toolCall                |
    And the transcript has entries matching:
      | type    | message.role |
      | message | toolResult   |

  # --- Error Handling ---

  @wip
  Scenario: Server unreachable
    Given the provider "anthropic" is configured with:
      | key     | value                  |
      | baseUrl | http://localhost:99999 |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating the server is unreachable
