Feature: Chat with Toad TUI
  The chat command can launch Toad as a terminal UI with Isaac
  registered as the ACP agent Toad connects to. Tests verify the
  command that would be launched, not the actual subprocess.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: --toad --dry-run prints the command that would launch Toad
    Given the command "toad" is available
    When isaac is run with "chat --toad --dry-run"
    Then the output contains "toad"
    And the output contains "isaac acp"
    And the exit code is 0

  Scenario: --toad reports a clear error when Toad is not installed
    Given the command "toad" is not available
    When isaac is run with "chat --toad"
    Then the output contains "Toad not found"
    And the output contains "batrachian.ai/install"
    And the exit code is 1

  @wip
  Scenario: --toad --model passes the model flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --toad --model grok --dry-run"
    Then the output contains "isaac acp --model grok"
    And the exit code is 0

  @wip
  Scenario: --toad --agent passes the agent flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --toad --agent grok --dry-run"
    Then the output contains "isaac acp --agent grok"
    And the exit code is 0
