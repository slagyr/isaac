Feature: Config Command
  `isaac config` inspects and validates configuration at ~/.isaac/config/.
  Operators can print the resolved (merged) config, look up a single value,
  list contributing files, or validate a staged change before writing it.
  Sensitive values sourced from ${VAR} substitution are redacted by default;
  --reveal requires typing "REVEAL" on stdin to surface real values.

  Background:
    Given an in-memory Isaac state directory "isaac-state"

  # ----- Help -----

  Scenario: config is registered and has help
    When isaac is run with "help config"
    Then the output matches:
      | pattern                                          |
      | Usage: isaac config \[subcommand\] \[options\]   |
      | Inspect and validate Isaac configuration         |
      | Subcommands:                                     |
      | validate\s+Validate config                       |
      | get <path>\s+Get a value by dotted key path      |
      | sources\s+List contributing config files         |
      | Options:                                         |
      | --raw\s+Print pre-substitution config            |
    And the exit code is 0

  # ----- Print (default / --raw / --reveal) -----

  Scenario: config redacts resolved ${VAR} values by default
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:api-key  "${CONFIG_TEST_API_KEY}"
                               :auth-key "${CONFIG_TEST_UNSET_KEY}"}}}
      """
    When isaac is run with "config"
    Then the output lines contain in order:
      | pattern                              |
      | :auth-key                            |
      | "<CONFIG_TEST_UNSET_KEY:UNRESOLVED>" |
      | :api-key                             |
      | "<CONFIG_TEST_API_KEY:redacted>"    |
    And the output has at least 5 lines
    And the output does not contain "sk-test-123"
    And the exit code is 0

  Scenario: config --raw prints pre-substitution values
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    When isaac is run with "config --raw"
    Then the output lines contain in order:
      | pattern                    |
      | :api-key                   |
      | "${CONFIG_TEST_API_KEY}"  |
    And the output does not contain "sk-test-123"
    And the output does not contain "redacted"
    And the exit code is 0

  Scenario: config --reveal shows real values after typed confirmation
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is:
      """
      REVEAL
      """
    When isaac is run with "config --reveal"
    Then the output lines contain in order:
      | pattern         |
      | :api-key        |
      | "sk-test-123"  |
    And the exit code is 0

  Scenario: config --reveal refuses without typed confirmation
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is empty
    When isaac is run with "config --reveal"
    Then the stderr contains "type REVEAL to confirm"
    And the output does not contain "sk-test-123"
    And the exit code is 1

  # ----- Sources -----

  Scenario: config sources lists contributing files
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}}
      """
    And config file "crew/marvin.edn" containing:
      """
      {:model :llama}
      """
    And config file "models/grover.edn" containing:
      """
      {:model "claude-opus-4-7" :provider :grover :context-window 200000}
      """
    When isaac is run with "config sources"
    Then the output matches:
      | pattern                    |
      | config/isaac\.edn          |
      | config/crew/marvin\.edn    |
      | config/models/grover\.edn  |
    And the exit code is 0

  # ----- Validate -----

  Scenario: validate passes for a well-formed config
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {:soul "You are Isaac."}}}
      """
    When isaac is run with "config validate"
    Then the output contains "OK"
    And the exit code is 0

  Scenario: validate reports errors with exit code 1
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :ghost :model :llama}}
      """
    When isaac is run with "config validate"
    Then the stderr matches:
      | pattern                                    |
      | defaults\.crew.*references undefined crew  |
    And the exit code is 1

  Scenario: validate reports warnings but still exits 0
    Given config file "isaac.edn" containing:
      """
      {:defaults     {:crew :main :model :llama}
       :crew         {:main {}}
       :experimental {:feature-flag true}}
      """
    When isaac is run with "config validate"
    Then the stderr matches:
      | pattern                      |
      | warning: :experimental       |
      | unknown key                  |
    And the output contains "OK"
    And the exit code is 0

  # ----- Validate overlay (--as) -----

  Scenario: validate --as overlays stdin as a specific file and validates the composition
    Given config file "crew/marvin.edn" containing:
      """
      {:model :llama}
      """
    And stdin is:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}}
      """
    When isaac is run with "config validate --as isaac.edn -"
    Then the output contains "OK"
    And the exit code is 0

  Scenario: validate --as catches composition errors from the overlay
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:marvin {:soul "Existing"}}}
      """
    And stdin is:
      """
      {:soul "Overlay"}
      """
    When isaac is run with "config validate --as crew/marvin.edn -"
    Then the stderr matches:
      | pattern                                                       |
      | crew\.marvin.*defined in both isaac\.edn and crew/marvin\.edn |
    And the exit code is 1

  # ----- Get -----

  Scenario: get prints a scalar value by dotted keyword path
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:marvin {:soul "You are Marvin."}}}
      """
    When isaac is run with "config get crew.marvin.soul"
    Then the output contains "You are Marvin."
    And the exit code is 0

  Scenario: get prints a nested structure as EDN
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:marvin {:model :llama :soul "You are Marvin."}}}
      """
    When isaac is run with "config get crew.marvin"
    Then the output lines contain in order:
      | pattern             |
      | :soul               |
      | "You are Marvin."  |
      | :model :llama       |
    And the exit code is 0

  Scenario: get exits non-zero for a missing key
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:marvin {:soul "You are Marvin."}}}
      """
    When isaac is run with "config get crew.marvin.nope"
    Then the stderr contains "not found: crew.marvin.nope"
    And the exit code is 1

  Scenario: get redacts resolved ${VAR} values by default
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    When isaac is run with "config get providers.anthropic.api-key"
    Then the output contains "<CONFIG_TEST_API_KEY:redacted>"
    And the output does not contain "sk-test-123"
    And the exit code is 0

  Scenario: get --reveal shows the real value after typed confirmation
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is:
      """
      REVEAL
      """
    When isaac is run with "config get providers.anthropic.api-key --reveal"
    Then the output contains "sk-test-123"
    And the exit code is 0
