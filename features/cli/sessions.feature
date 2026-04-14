Feature: Sessions Command
  `isaac sessions` lists stored conversation sessions.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |

  Scenario: sessions is registered and has help
    When isaac is run with "help sessions"
    Then the output contains "Usage: isaac sessions"
    And the exit code is 0

  Scenario: sessions lists all sessions
    Given the following sessions exist:
      | name         | totalTokens | updatedAt           |
      | design-chat  | 5000        | 2026-04-12T15:00:00 |
      | review-chat  | 778         | 2026-04-12T10:00:00 |
      | pirate-chat  | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions"
    Then the output matches:
      | pattern      |
      | design-chat  |
      | review-chat  |
      | pirate-chat  |
    And the exit code is 0

  Scenario: sessions --agent filters by current agent
    Given the following sessions exist:
      | name         | agent | totalTokens | updatedAt           |
      | design-chat  | main  | 5000        | 2026-04-12T15:00:00 |
      | pirate-chat  | ketch | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions --agent ketch"
    Then the output matches:
      | pattern      |
      | pirate-chat  |
    And the output does not contain "design-chat"
    And the exit code is 0

  Scenario: sessions with no sessions prints a message
    When isaac is run with "sessions"
    Then the output contains "no sessions"
    And the exit code is 0

  Scenario: sessions --agent with unknown agent prints an error
    When isaac is run with "sessions --agent nonexistent"
    Then the stderr contains "unknown agent"
    And the stderr contains "nonexistent"
    And the exit code is 1
