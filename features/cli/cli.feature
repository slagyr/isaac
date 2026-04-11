Feature: CLI
  Isaac provides a command-line interface with discoverable
  commands and built-in help.

  Scenario: No arguments shows usage
    When isaac is run with ""
    Then the output contains "Usage: isaac <command>"
    And the output contains "Commands:"
    And the exit code is 0

  Scenario: Unknown command shows error and usage
    When isaac is run with "bogus"
    Then the output contains "Unknown command: bogus"
    And the output contains "Usage: isaac <command>"
    And the exit code is 1

  Scenario: Help for a known command
    When isaac is run with "help chat"
    Then the output contains "Usage: isaac chat"
    And the output contains "Options:"
    And the exit code is 0

  Scenario: Help for an unknown command
    When isaac is run with "help bogus"
    Then the output contains "Unknown command: bogus"
    And the exit code is 1

  Scenario: Command --help flag
    When isaac is run with "chat --help"
    Then the output contains "Usage: isaac chat"
    And the output contains "Options:"
    And the exit code is 0

  Scenario: Top-level --help flag shows usage
    When isaac is run with "--help"
    Then the output contains "Usage: isaac <command>"
    And the output contains "Commands:"
    And the exit code is 0

  Scenario: Top-level -h flag shows usage
    When isaac is run with "-h"
    Then the output contains "Usage: isaac <command>"
    And the output contains "Commands:"
    And the exit code is 0
