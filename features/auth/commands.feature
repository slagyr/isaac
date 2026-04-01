@wip
Feature: Auth Commands
  Isaac provides commands to manage authentication credentials
  for LLM providers. OpenClaw-compatible aliases are supported
  via "models auth" for drop-in compatibility.

  # --- Login ---

  Scenario: Login with Anthropic OAuth via Claude Code
    Given Claude Code is logged in
    When isaac is run with "auth login --provider anthropic"
    Then the output contains "Authenticated"
    And the exit code is 0

  Scenario: Login with Anthropic API key
    When isaac is run with "auth login --provider anthropic --api-key"
    Then the output prompts for an API key
    And the exit code is 0

  Scenario: Login without specifying provider
    When isaac is run with "auth login"
    Then the output contains "Usage:"
    And the output contains "--provider"
    And the exit code is 1

  Scenario: Login with unknown provider
    When isaac is run with "auth login --provider bogus"
    Then the output contains "Unknown provider: bogus"
    And the exit code is 1

  # --- Status ---

  Scenario: Show auth status
    When isaac is run with "auth status"
    Then the output contains "ollama"
    And the exit code is 0

  # --- Logout ---

  Scenario: Logout from a provider
    Given authenticated credentials exist for provider "anthropic"
    When isaac is run with "auth logout --provider anthropic"
    Then the output contains "Logged out"
    And credentials for "anthropic" are removed
    And the exit code is 0

  # --- OpenClaw Aliases ---

  Scenario: OpenClaw-compatible auth command
    Given Claude Code is logged in
    When isaac is run with "models auth login --provider anthropic"
    Then the output contains "Authenticated"
    And the exit code is 0

  # --- Help ---

  Scenario: Auth help
    When isaac is run with "auth --help"
    Then the output contains "Usage: isaac auth"
    And the output contains "login"
    And the output contains "status"
    And the output contains "logout"
    And the exit code is 0
