Feature: Crew Command
  `isaac crew` lists configured crew members with their model,
  provider, and soul source.

  Background:
    Given an in-memory Isaac state directory "target/test-state"

  Scenario: crew is registered and has help
    When isaac is run with "help crew"
    Then the output contains "Usage: isaac crew"
    And the exit code is 0

  Scenario: crew lists configured crew members with underlined headers
    Given the following models exist:
      | alias  | model | provider | context-window |
      | grover | echo  | grover   | 32768          |
    And the following crew exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |
    When isaac is run with "crew"
    Then the output matches:
      | pattern              |
      | Name .* Model .* Provider .* Soul |
      | ─+.*─+.*─+.*─+      |
      | main .* echo         |
      | ketch .* echo        |
    And the exit code is 0

  Scenario: crew with no configured crew members shows the default
    When isaac is run with "crew"
    Then the output matches:
      | pattern              |
      | Name .* Model .* Provider .* Soul |
      | ─+                   |
      | main                 |
    And the exit code is 0
