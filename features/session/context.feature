Feature: Turn context resolution
  Resolving per-turn context (soul, model, provider) from a session
  key and config. This is the single source of truth — channels
  never resolve these independently.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the isaac EDN file "config/models/grover.edn" exists with:
      | path | value |
      | model | echo |
      | provider | grover |
      | context-window | 32768 |

  Scenario: soul resolved from workspace SOUL.md
    Given workspace "main" in "target/test-state" has SOUL.md:
      """
      You are Dr. Prattlesworth, a Victorian recluse.
      """
    When turn context is resolved for crew "main"
    Then the resolved soul contains "Prattlesworth"

  Scenario: soul falls back to default when no SOUL.md exists
    When turn context is resolved for crew "main"
    Then the resolved soul is "You are Isaac, a helpful AI assistant."

  Scenario: soul from crew config takes precedence over SOUL.md
    Given workspace "main" in "target/test-state" has SOUL.md:
      """
      You are Dr. Prattlesworth, a Victorian recluse.
      """
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | Custom soul text |
    When turn context is resolved for crew "main"
    Then the resolved soul is "Custom soul text"

  Scenario: model and provider resolved from config defaults
    When turn context is resolved for crew "main"
    Then the resolved model is not nil
    And the resolved provider is not nil
