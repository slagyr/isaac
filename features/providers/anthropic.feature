Feature: Anthropic Provider
  Isaac can use Anthropic's Claude models via the Messages API,
  supporting both API key and OAuth authentication.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | claude |

  # --- Authentication ---

  @wip
  Scenario: Authenticate with API key
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the request header "x-api-key" is present
    And the request header "anthropic-version" is present

  @wip
  Scenario: Authenticate with OAuth token
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | oauth                     |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the request header "Authorization" matches #"Bearer .+"

  # --- Request Format ---

  Scenario: System prompt is a separate field
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following sessions exist:
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

  Scenario: Prompt caching breakpoints are applied
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following sessions exist:
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
  Scenario: Send a message and receive a response
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following sessions exist:
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
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following sessions exist:
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

  # --- Tool Calling ---

  @wip
  Scenario: Tool call with Anthropic format
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the agent has tools:
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
  Scenario: Invalid API key
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the provider "anthropic" is configured with:
      | key    | value          |
      | auth   | api-key        |
      | apiKey | sk-ant-invalid |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating authentication failed

  # --- Integration ---

  @wip @slow
  Scenario: Live Anthropic API call
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content     |
      | user | Say "hello" |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |

  @wip @slow
  Scenario: Live Anthropic streaming
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the provider "anthropic" is configured with:
      | key     | value                     |
      | auth    | api-key                   |
      | apiKey  | ${ANTHROPIC_API_KEY}      |
      | baseUrl | https://api.anthropic.com |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content         |
      | user | Tell me a story |
    When the prompt is streamed to the LLM
    Then response chunks arrive incrementally
    And the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |
