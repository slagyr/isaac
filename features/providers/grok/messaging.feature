Feature: Grok Messaging
  Grok uses the OpenAI-compatible chat completions API.
  See features/providers/openai/messaging.feature for the
  shared format tests. This file covers Grok-specific behavior
  only.

  # --- Response Model ---

  Scenario: Provider-returned model version is stored in transcript
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model  | provider | contextWindow |
      | grok  | grok-3 | grover   | 131072        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    And the following model responses are queued:
      | content | model              |
      | Hi!     | grok-3-20250710    |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.model   | message.provider |
      | message | assistant    | grok-3-20250710 | grover           |

  Scenario: Configured model is stored when provider returns no model
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model  | provider | contextWindow |
      | grok  | grok-3 | grover   | 131072        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    And the following model responses are queued:
      | content | model |
      | Hi!     |       |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.model |
      | message | assistant    | grok-3        |
