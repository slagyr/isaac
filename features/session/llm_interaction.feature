Feature: LLM Interaction
  Isaac sends prompts to LLM providers and records responses
  in the session transcript.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |

  # --- Basic Chat ---

  Scenario: Send a message and receive a response
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When the user sends "What is 2+2?" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.model | message.provider |
      | message | assistant    | echo          | grover           |
    And agent "main" has sessions matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  Scenario: Streaming response
    Given the following model responses are queued:
      | type | content                | model |
      | text | Once upon a time...    | echo  |
    When the user sends "Tell me a story" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role |
      | message | assistant    |

  # --- Tool Calling ---

  @wip
  Scenario: Model requests a tool call and receives the result
    Given the built-in tools are registered
    And the agent has tools:
      | name | description      | parameters             |
      | exec | Run a command    | {"command": "string"}  |
    And the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    When the user sends "Run echo hi" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.name | message.content |
      | message | assistant    | exec         |                 |
      | message | toolResult   |              | #"hi"           |
      | message | assistant    |              | #".+"           |

  # --- Error Handling ---

  Scenario: LLM server is unavailable
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
    And config:
      | key        | value  |
      | log.output | memory |
    When the user sends "Hello" on session "agent:main:cli:direct:user1"
    Then the log has entries matching:
      | level  | event                 |
      | :error | :chat/response-failed |
