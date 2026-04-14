@wip
Feature: Tool Loop Message Format
  When the tool dispatch loop sends tool call results back to
  the LLM for the next round, the messages must be formatted
  correctly for each provider. OpenAI requires type:function
  on tool_calls and role:tool with tool_call_id on results.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And the built-in tools are registered

  Scenario: tool loop formats messages for OpenAI-compatible providers
    Given the following sessions exist:
      | name      |
      | loop-test |
    And the provider "openai" is configured with:
      | key     | value                     |
      | baseUrl | https://api.openai.com/v1 |
      | api     | openai-compatible         |
    And the following model responses are queued:
      | tool_call | arguments                        |
      | exec      | {"command": "echo Hieronymus"}    |
    And the following model responses are queued:
      | type | content                       | model |
      | text | The tortoise says Hieronymus. | echo  |
    When the user sends "ask the tortoise his name" on session "loop-test"
    Then the tool loop request contains messages with:
      | role      | tool_calls[0].type | tool_call_id |
      | assistant | function           |              |
      | tool      |                    | #*           |

  Scenario: tool loop works across multiple rounds without type errors
    Given the following sessions exist:
      | name      |
      | loop-test |
    And the following model responses are queued:
      | tool_call | arguments                     |
      | read      | {"filePath": "fridge.txt"}    |
    And the following model responses are queued:
      | tool_call | arguments                     |
      | exec      | {"command": "echo still sad"} |
    And the following model responses are queued:
      | type | content                                  | model |
      | text | The lemon is still sad after two checks. | echo  |
    When the user sends "double check the fridge" on session "loop-test"
    Then session "loop-test" has transcript matching:
      | type    | message.role | message.content                          |
      | message | assistant    | The lemon is still sad after two checks. |
