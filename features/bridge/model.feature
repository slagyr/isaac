@wip
Feature: /model Command
  The /model bridge command switches the session's active model.
  Subsequent turns use the new model. The change is stored in
  the session, not the channel.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias   | model         | provider | contextWindow |
      | grover  | echo          | grover   | 32768         |
      | grover2 | echo-alt      | grover   | 16384         |
      | grok    | grok-4-1-fast | grok     | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: /model switches the session's model
    Given the following sessions exist:
      | name       |
      | model-test |
    When the user sends "/model grok" on session "model-test"
    Then the output contains "switched model to grok-4-1-fast"
    And the following sessions match:
      | id         | model         | provider |
      | model-test | grok-4-1-fast | grok     |

  Scenario: /model persists across turns
    Given the following sessions exist:
      | name       |
      | model-test |
    And the following model responses are queued:
      | type | content | model    |
      | text | Hello   | echo-alt |
      | text | World   | echo-alt |
    When the user sends "/model grover2" on session "model-test"
    And the user sends "hi" on session "model-test"
    And the user sends "bye" on session "model-test"
    Then session "model-test" has transcript matching:
      | type    | message.role | message.model |
      | message | assistant    | echo-alt      |
      | message | assistant    | echo-alt      |

  Scenario: /model with no argument shows the current model
    Given the following sessions exist:
      | name       |
      | model-test |
    When the user sends "/model" on session "model-test"
    Then the output contains "echo is the current model"

  Scenario: /model with unknown alias shows an error
    Given the following sessions exist:
      | name       |
      | model-test |
    When the user sends "/model nonexistent" on session "model-test"
    Then the output contains "unknown model: nonexistent"
