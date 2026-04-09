Feature: Ollama Live Integration
  Isaac can talk to a real local Ollama server.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model           | provider | contextWindow |
      | local | llama3.2:latest | ollama   | 32000         |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | local |

  @slow
  Scenario: Live Ollama chat
    Given the Ollama server is running
    And model "llama3.2:latest" is available in Ollama
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Say "hello"     |
    When the user sends "Say \"hello\"" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.provider |
      | message | assistant    | ollama           |

  @slow
  Scenario: Missing Ollama server reports a clear error
    Given the Ollama server is not running
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Hello           |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                  |
      | :error | :chat/response-failed  |
