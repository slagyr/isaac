Feature: Chat Command
  `isaac chat` launches Toad as a terminal UI with Isaac registered
  as the ACP agent. Flags are passed through to the `isaac acp`
  subprocess. Tests verify the command that would be launched, not
  the actual subprocess.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: chat launches Toad by default
    Given the command "toad" is available
    When isaac is run with "chat --dry-run"
    Then the output contains "toad"
    And the output contains "isaac acp"
    And the exit code is 0

  Scenario: chat reports a clear error when Toad is not installed
    Given the command "toad" is not available
    When isaac is run with "chat"
    Then the output contains "Toad not found"
    And the output contains "batrachian.ai/install"
    And the exit code is 1

  Scenario: --resume passes the resume flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --resume --dry-run"
    Then the output contains "isaac acp --resume"
    And the exit code is 0

  Scenario: --crew passes the crew flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --crew ketch --dry-run"
    Then the output contains "isaac acp --crew ketch"
    And the exit code is 0

  Scenario: --model passes the model flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --model grok --dry-run"
    Then the output contains "isaac acp --model grok"
    And the exit code is 0

  Scenario: --remote passes the remote flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --remote ws://host:6674/acp --dry-run"
    Then the output contains "isaac acp --remote ws://host:6674/acp"
    And the exit code is 0

  Scenario: --session passes the session flag to the acp subprocess
    Given the command "toad" is available
    When isaac is run with "chat --session my-session --dry-run"
    Then the output contains "isaac acp --session my-session"
    And the exit code is 0

  Scenario: multiple flags combine in the acp subprocess command
    Given the command "toad" is available
    When isaac is run with "chat --crew ketch --resume --dry-run"
    Then the output contains "isaac acp --crew ketch --resume"
    And the exit code is 0
