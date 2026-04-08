@wip
Feature: Chat and Provider Logging
  Isaac logs chat and provider lifecycle events with structured context.

  Background:
    Given config:
      | key        | value  |
      | log.output | memory |

  Scenario: Provider failure is logged with chat context
    Given the provider "ollama" is configured with:
      | key     | value                  |
      | baseUrl | http://localhost:99999 |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating the server is unreachable
    And the log has entries matching:
      | level  | event                 | provider | session                      |
      | :error | :chat/response-failed | ollama   | agent:main:cli:direct:user1 |

  Scenario: Successful chat response storage is logged at debug
    Given the following model responses are queued:
      | type | content | model      |
      | text | Hello   | test-model |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hi      |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role |
      | message | assistant    |
    And the log has entries matching:
      | level  | event                | session                      | model      |
      | :debug | :chat/message-stored | agent:main:cli:direct:user1 | test-model |

  Scenario: Streaming completion is logged at debug
    Given the following model responses are queued:
      | type | content      | model      |
      | text | streamed hi  | test-model |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hi      |
    When the prompt is streamed to the LLM
    Then response chunks arrive incrementally
    And the log has entries matching:
      | level  | event                  | session                      |
      | :debug | :chat/stream-completed | agent:main:cli:direct:user1 |
