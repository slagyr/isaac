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
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                 | provider | session                      |
      | :error | :chat/response-failed | ollama   | agent:main:cli:direct:user1 |

  Scenario: Successful chat response storage is logged at debug
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    When the user sends "Hi" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role |
      | message | assistant    |
    And the log has entries matching:
      | level  | event                | session                      | model |
      | :debug | :session/message-stored | agent:main:cli:direct:user1 | echo  |

  Scenario: Streaming completion is logged at debug
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following model responses are queued:
      | type | content | model |
      | text | Hi back | echo  |
    When the user sends "Hi" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                  | session                      |
      | :debug | :session/stream-completed | agent:main:cli:direct:user1 |

  Scenario: Compaction check and start are logged during chat
    Given agent "main" has sessions:
      | key                         | totalTokens | #comment                     |
      | agent:main:cli:direct:user1 | 30000       | exceeds 90% of 32768 window  |
    And the following model responses are queued:
      | type | content               | model |
      | text | Summary of prior chat | echo  |
      | text | Here is my answer     | echo  |
    When the user sends "Continue" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                       | session                      |
      | :debug | :session/compaction-check   | agent:main:cli:direct:user1 |
      | :info  | :session/compaction-started | agent:main:cli:direct:user1 |

  Scenario: Compaction entry precedes the triggering user message in transcript
    Given agent "main" has sessions:
      | key                         | totalTokens | #comment                     |
      | agent:main:cli:direct:user1 | 30000       | exceeds 90% of 32768 window  |
    And the following model responses are queued:
      | type | content               | model |
      | text | Summary of prior chat | echo  |
      | text | Here is my answer     | echo  |
    When the user sends "Continue" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | #index | type       |
      | 1      | compaction |
      | 2      | message    |
