@wip
Feature: Ollama Messaging
  Isaac can use Ollama's chat API for local model inference.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model  | provider       | contextWindow |
      | ernie  | ernie  | grover:ollama  | 32000         |
    And the following crew exist:
      | name  | soul                       | model |
      | ernie | Rubber ducky enthusiast.   | ernie |

  Scenario: Request uses Ollama chat format
    Given the following sessions exist:
      | name      | crew  |
      | bath-time | ernie |
    And session "bath-time" has transcript:
      | type    | message.role | message.content               |
      | message | user         | Have you seen my rubber ducky? |
    When the prompt "Have you seen my rubber ducky?" on session "bath-time" matches:
      | key                 | value                       |
      | model               | ernie                       |
      | messages[0].role    | system                      |
      | messages[0].content | Rubber ducky enthusiast.    |
      | messages[1].role    | user                        |
      | messages[1].content | Have you seen my rubber ducky? |
