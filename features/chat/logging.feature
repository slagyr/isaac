@wip
Feature: Chat and Provider Logging
  Isaac logs chat and provider lifecycle events with structured context.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: Provider failure is logged with chat context
    Given the following models exist:
      | alias | model           | provider | contextWindow |
      | local | llama3.2:latest | ollama   | 32000         |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | local |
    And the provider "ollama" is configured with:
      | key     | value                  |
      | baseUrl | http://localhost:99999 |
    And the following sessions exist:
      | name          |
      | log-fail-test |
    When the user sends "Hello" on session "log-fail-test"
    Then the log has entries matching:
      | level  | event                 | provider | session       |
      | :error | :chat/response-failed | ollama   | log-fail-test |

  Scenario: Successful chat response storage is logged at debug
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And the following sessions exist:
      | name             |
      | log-success-test |
    When the user sends "Hi" on session "log-success-test"
    Then session "log-success-test" has transcript matching:
      | type    | message.role |
      | message | assistant    |
    And the log has entries matching:
      | level  | event                   | session          | model |
      | :debug | :session/message-stored | log-success-test | echo  |

  Scenario: Streaming completion is logged at debug
    Given the following sessions exist:
      | name            |
      | log-stream-test |
    And the following model responses are queued:
      | type | content | model |
      | text | Hi back | echo  |
    When the user sends "Hi" on session "log-stream-test"
    Then the log has entries matching:
      | level  | event                     | session         |
      | :debug | :session/stream-completed | log-stream-test |

  Scenario: Compaction check and start are logged during chat
    Given the following sessions exist:
      | name              | totalTokens | #comment                     |
      | log-compact-test  | 30000       | exceeds 90% of 32768 window  |
    And the following model responses are queued:
      | type | content               | model |
      | text | Summary of prior chat | echo  |
      | text | Here is my answer     | echo  |
    When the user sends "Continue" on session "log-compact-test"
    Then the log has entries matching:
      | level  | event                       | session          |
      | :debug | :session/compaction-check   | log-compact-test |
      | :info  | :session/compaction-started | log-compact-test |

  Scenario: Compaction entry precedes the triggering user message in transcript
    Given the following sessions exist:
      | name             | totalTokens | #comment                     |
      | log-order-test   | 30000       | exceeds 90% of 32768 window  |
    And the following model responses are queued:
      | type | content               | model |
      | text | Summary of prior chat | echo  |
      | text | Here is my answer     | echo  |
    When the user sends "Continue" on session "log-order-test"
    Then session "log-order-test" has transcript matching:
      | #index | type       |
      | 1      | compaction |
      | 2      | message    |
