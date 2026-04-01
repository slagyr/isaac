@wip
Feature: OpenAI Messaging
  Isaac can use OpenAI's GPT models via the chat completions API.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model  | provider | contextWindow |
      | gpt   | gpt-5  | openai   | 128000        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | gpt   |
    And the provider "openai" is configured with:
      | key     | value                       |
      | apiKey  | ${OPENAI_API_KEY}           |
      | baseUrl | https://api.openai.com/v1   |
      | api     | openai-compatible           |

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
      | model               | gpt-5          |
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
      | type    | message.role | message.model | message.provider |
      | message | assistant    | gpt-5         | openai           |
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
