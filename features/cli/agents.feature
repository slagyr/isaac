Feature: Agents Command
  `isaac agents` lists configured agents with their model,
  provider, and soul source.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: agents is registered and has help
    When isaac is run with "help agents"
    Then the output contains "Usage: isaac agents"
    And the exit code is 0

  Scenario: agents lists configured agents
    Given the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |
    When isaac is run with "agents"
    Then the output matches:
      | pattern              |
      | main .* echo         |
      | ketch .* echo        |
    And the exit code is 0

  Scenario: agents with no configured agents shows the default
    When isaac is run with "agents"
    Then the output matches:
      | pattern     |
      | main        |
    And the exit code is 0
