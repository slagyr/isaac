@slow
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
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the prompt "Hello" on session "agent:main:cli:direct:user1" matches:
      | key                 | value          |
      | model               | gpt-5          |
      | messages[0].role    | system         |
      | messages[0].content | You are Isaac. |
      | messages[1].role    | user           |
      | messages[1].content | Hello          |

  # --- Response Handling ---

  Scenario: Parse a response into a transcript entry
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | What is 2+2?    |
    When the user sends "What is 2+2?" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.model | message.provider |
      | message | assistant    | gpt-5         | openai           |
    And agent "main" has sessions matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  # --- Streaming ---

  Scenario: Streaming response
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

  Scenario: Tool call with OpenAI format
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
