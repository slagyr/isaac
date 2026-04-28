Feature: session_state tool
  The session_state tool reports the current session's crew, model,
  provider, session key, origin, timing, context usage, and
  compaction count. The LLM calls it to answer "what model are
  you?" and to switch its own model mid-conversation. Crew is
  read-only — switching crews would let the agent escalate its
  own tool permissions.

  The model arg switches the session's model to a configured alias.
  The reset-model arg clears the per-session override and falls
  back to the crew's currently configured model. Passing both is
  an error.

  Background:
    Given default Grover setup

  @wip
  Scenario: session_state reports current crew, model, provider, origin, and timing
    Given the current time is "2026-04-27T10:00:00Z"
    And the following sessions exist:
      | name        | crew |
      | status-test | main |
    When the tool "session_state" is called with:
      | session-key | status-test |
    Then the tool result is not an error
    And the tool result JSON has:
      | path           | value                |
      | crew           | main                 |
      | model.alias    | grover               |
      | model.upstream | echo                 |
      | provider       | grover               |
      | session        | status-test          |
      | origin.kind    | cli                  |
      | created-at     | 2026-04-27T10:00:00Z |
      | updated-at     | 2026-04-27T10:00:00Z |
      | context.used   | 0                    |
      | context.window | 32768                |
      | compactions    | 0                    |

  @wip
  Scenario: session_state switches the session's model when model arg is provided
    Given the isaac file "config/models/parrot.edn" exists with:
      """
      {:model "squawk" :provider :grover :context-window 16384}
      """
    And the following sessions exist:
      | name        | crew |
      | status-test | main |
    When the tool "session_state" is called with:
      | session-key | status-test |
      | model       | parrot      |
    Then the tool result is not an error
    And the tool result JSON has:
      | path           | value  |
      | model.alias    | parrot |
      | model.upstream | squawk |
    And session "status-test" matches:
      | key   | value  |
      | model | parrot |

  @wip
  Scenario: session_state reverts to the crew's default model when reset-model is true
    Given the isaac file "config/models/parrot.edn" exists with:
      """
      {:model "squawk" :provider :grover :context-window 16384}
      """
    And the following sessions exist:
      | name        | crew | model  |
      | status-test | main | parrot |
    When the tool "session_state" is called with:
      | session-key | status-test |
      | reset-model | true        |
    Then the tool result is not an error
    And the tool result JSON has:
      | path           | value  |
      | model.alias    | grover |
      | model.upstream | echo   |
    And session "status-test" matches:
      | key   | value  |
      | model | grover |

  @wip
  Scenario: session_state errors when both model and reset-model are provided
    Given the following sessions exist:
      | name        | crew |
      | status-test | main |
    When the tool "session_state" is called with:
      | session-key | status-test |
      | model       | grover      |
      | reset-model | true        |
    Then the tool result is an error
    And the tool result contains "model and reset-model are mutually exclusive"

  @wip
  Scenario: session_state errors when given an unknown model alias
    Given the following sessions exist:
      | name        | crew |
      | status-test | main |
    When the tool "session_state" is called with:
      | session-key | status-test |
      | model       | nonexistent |
    Then the tool result is an error
    And the tool result contains "unknown model: nonexistent"

  @wip
  Scenario: session_state reports origin name when the session was started by a webhook
    Given the following sessions exist:
      | name         | crew | origin.kind | origin.name |
      | hook:lettuce | main | webhook     | lettuce     |
    When the tool "session_state" is called with:
      | session-key | hook:lettuce |
    Then the tool result is not an error
    And the tool result JSON has:
      | path        | value   |
      | origin.kind | webhook |
      | origin.name | lettuce |
