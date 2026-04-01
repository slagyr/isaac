@wip
Feature: Grok Messaging
  Isaac can use xAI's Grok models via the OpenAI-compatible
  chat completions API.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model      | provider | contextWindow |
      | grok  | grok-4-1-fast | grok  | 131072        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |
    And the provider "grok" is configured with:
      | key     | value                |
      | apiKey  | ${GROK_API_KEY}      |
      | baseUrl | https://api.x.ai/v1 |
      | api     | openai-compatible    |

  # --- Request Format ---

  Scenario: Request uses OpenAI chat completions format
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When a prompt is built for the session
    Then the prompt matches:
      | key                 | value          |
      | model               | grok-4-1-fast  |
      | messages[0].role    | system         |
      | messages[0].content | You are Isaac. |
      | messages[1].role    | user           |
      | messages[1].content | Hello          |

  # --- Response Handling ---

  Scenario: Parse a response into a transcript entry
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content      |
      | user | What is 2+2? |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.model  | message.provider |
      | message | assistant    | grok-4-1-fast  | grok             |
    And the session listing has entries matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  # --- Streaming ---

  Scenario: Streaming response
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

  Scenario: Tool call with OpenAI format
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
