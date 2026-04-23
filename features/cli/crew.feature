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
    Given the isaac EDN file "config/models/grover.edn" exists with:
      | path | value |
      | model | echo |
      | provider | grover |
      | context-window | 32768 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are Isaac. |
    And the isaac EDN file "config/crew/ketch.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are a pirate. |
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
