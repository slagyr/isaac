Feature: Ollama Live Integration
  Isaac can talk to a real local Ollama server.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model        | provider | context-window |
      | local | llama3.2:1b  | ollama   | 32000          |
    And the following crew exist:
      | name | soul           | model |
      | main | You are Isaac. | local |

  @slow
  Scenario: Live Ollama chat
    Given the Ollama server is running
    And model "llama3.2:1b" is available in Ollama
    And the following sessions exist:
      | name        |
      | ollama-live |
    And session "ollama-live" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "ollama-live"
    Then session "ollama-live" has transcript matching:
      | type    | message.role | message.provider |
      | message | assistant    | ollama           |

  @slow
  Scenario: Missing Ollama server reports a clear error
    Given the Ollama server is not running
    And the following sessions exist:
      | name         |
      | ollama-error |
    And session "ollama-error" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "ollama-error"
    Then the log has entries matching:
      | level  | event                  |
      | :error | :chat/response-failed  |
