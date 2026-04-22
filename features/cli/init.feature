Feature: isaac init
  Scaffolds a default config for fresh installs. Ollama-first so users
  can run Isaac without arranging API keys first. Refuses to clobber
  an existing config. Pairs with an updated 'no config found' error
  that points users at isaac init.

  Scaffolded files (at <home>/config/):
    - isaac.edn        :defaults, :tz, :prefer-entity-files?, :cron {:heartbeat ...}
    - crew/main.edn    {:model :default}
    - crew/main.md     starter soul
    - models/default.edn    Ollama model reference
    - providers/ollama.edn  local Ollama provider

  Background:
    Given the user home directory is "/tmp/user"

  Scenario: isaac init scaffolds a usable config at a fresh home
    Given an empty isaac home at "target/test-state"
    When isaac is run with "--home target/test-state init"
    Then the exit code is 0
    And the output contains "Isaac initialized"
    And the output contains "ollama"
    And the state file "config/isaac.edn" exists
    And the state file "config/crew/main.md" exists

  Scenario: isaac init refuses when a config already exists
    Given a file "/tmp/user/.isaac/config/isaac.edn" exists with content "{:defaults {:crew :main :model :llama}}"
    When isaac is run with "init"
    Then the stderr contains "config already exists"
    And the exit code is 1

  Scenario: the scaffolded config validates successfully
    Given an empty isaac home at "target/test-state"
    And isaac is run with "--home target/test-state init"
    When isaac is run with "--home target/test-state config validate"
    Then the output contains "OK"
    And the exit code is 0
