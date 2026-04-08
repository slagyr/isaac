@wip
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
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content     |
      | user | Say "hello" |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | ollama           |

  @slow
  Scenario: Live Ollama streaming
    Given the Ollama server is running
    And model "llama3.2:latest" is available in Ollama
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
      | message | assistant    | ollama           |

  @slow
  Scenario: Missing Ollama server reports a clear error
    Given the Ollama server is not running
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating the server is unreachable
