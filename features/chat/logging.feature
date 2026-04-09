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
      | content | model      |
      | Hello   | test-model |
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
    Given the following sessions exist:
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

  Scenario: Compaction check and start are logged during chat
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the session totalTokens exceeds 90% of the context window
    When the next user message is sent
    Then the log has entries matching:
      | level  | event                       | session                      |
      | :debug | :context/compaction-check   | agent:main:cli:direct:user1 |
      | :info  | :context/compaction-started | agent:main:cli:direct:user1 |

  Scenario: Compaction entry precedes the triggering user message in transcript
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the session totalTokens exceeds 90% of the context window
    When the next user message is sent
    Then the transcript has entries matching:
      | #index | type       |
      | 1      | compaction |
      | 2      | message    |
