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
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |

  # --- Basic Chat ---

  Scenario: Send a message and receive a response
    Given the following messages are appended:
      | role | content      |
      | user | What is 2+2? |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.model   | message.provider |
      | message | assistant    | echo | grover           |
    And the session listing has entries matching:
      | key                         | inputTokens | outputTokens |
      | agent:main:cli:direct:user1 | #"\d+"      | #"\d+"       |

  Scenario: Streaming response
    Given the following messages are appended:
      | role | content         |
      | user | Tell me a story |
    When the prompt is streamed to the LLM
    Then response chunks arrive incrementally
    And the transcript has entries matching:
      | type    | message.role |
      | message | assistant    |

  # --- Tool Calling ---

  Scenario: Model requests a tool call
    Given the agent has tools:
      | name      | description            | parameters         |
      | read_file | Read a file's contents | {"path": "string"} |
    And the following model responses are queued:
      | tool_call | arguments          |
      | read_file | {"path": "README"} |
    And the following messages are appended:
      | role | content         |
      | user | Read the README |
    When the prompt is sent to the LLM
    And the model responds with a tool call
    Then the transcript has entries matching:
      | type    | message.role | message.content[0].type |
      | message | assistant    | toolCall                |
    And the transcript has entries matching:
      | type    | message.role |
      | message | toolResult   |
    And the transcript has entries matching:
      | type    | message.role | message.model   |
      | message | assistant    | echo |

  # --- Error Handling ---

  Scenario: LLM server is unavailable
    Given the LLM server is not running
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating the server is unreachable
    And the transcript has no new entries after the user message
