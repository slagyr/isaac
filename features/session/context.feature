Feature: Turn context resolution
  Resolving per-turn context (soul, model, provider) from a session
  key and config. This is the single source of truth — channels
  never resolve these independently.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |

  Scenario: soul resolved from workspace SOUL.md
    Given workspace "main" in "target/test-state" has SOUL.md:
      """
      You are Dr. Prattlesworth, a Victorian recluse.
      """
    When turn context is resolved for agent "main"
    Then the resolved soul contains "Prattlesworth"

  Scenario: soul falls back to default when no SOUL.md exists
    When turn context is resolved for agent "main"
    Then the resolved soul is "You are Isaac, a helpful AI assistant."

  Scenario: soul from agent config takes precedence over SOUL.md
    Given workspace "main" in "target/test-state" has SOUL.md:
      """
      You are Dr. Prattlesworth, a Victorian recluse.
      """
    And the following agents exist:
      | name | soul             | model  |
      | main | Custom soul text | grover |
    When turn context is resolved for agent "main"
    Then the resolved soul is "Custom soul text"

  Scenario: model and provider resolved from config defaults
    When turn context is resolved for agent "main"
    Then the resolved model is not nil
    And the resolved provider is not nil
