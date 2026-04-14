Feature: Ollama Messaging
  Isaac can use Ollama's chat API for local model inference.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model           | provider | contextWindow |
      | local | llama3.2:latest | ollama   | 32000         |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |

  Scenario: Request uses Ollama chat format
    Given the following sessions exist:
      | name          |
      | ollama-format |
    And session "ollama-format" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the prompt "Hello" on session "ollama-format" matches:
      | key                 | value           |
      | model               | llama3.2:latest |
      | messages[0].role    | system          |
      | messages[0].content | You are Isaac.  |
      | messages[1].role    | user            |
      | messages[1].content | Hello           |
