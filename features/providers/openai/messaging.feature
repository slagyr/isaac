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

  # --- Tool Calling ---

  @wip @slow
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
