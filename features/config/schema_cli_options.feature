Feature: isaac config schema CLI shows allowed values for dynamic fields

  `isaac config schema <path>` is config-aware: it reads `:modules` from
  the on-disk config and consults each declared module's manifest when
  rendering schema paths under `:comms`, `:providers`, `:tools`, and
  `:slash-commands`. Manifest-supplied fields surface with an automatic
  `[type-name]` (or `[tool-name]`/`[command-name]`) prefix in their
  description so the user sees which manifest contributes which field.
  Aggregate views (e.g. `comms.value`) render every known `:type`
  variant; when two variants happen to declare the same field name,
  each appears as its own entry distinguished by the prefix.

  Scenario: comm slot :type lists user-configurable comm kinds from manifests
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:defaults  {:crew :main :model :local}
       :crew      {:main {}}
       :models    {:local {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}
       :modules   {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}}
      """
    When isaac is run with "config schema comms.value.type"
    Then the stdout matches:
      | pattern         |
      | options:.*telly |
    And the stdout does not match:
      | pattern          |
      | options:.*acp    |
      | options:.*cli    |
      | options:.*hooks  |
      | options:.*memory |
      | options:.*null   |
    And the exit code is 0

  Scenario: config schema renders manifest-supplied comm fields with provenance prefix
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}}
      """
    When isaac is run with "config schema comms.value.loft"
    Then the stdout matches:
      | pattern        |
      | \[telly\]      |
      | type:\s+string |
    And the exit code is 0

  Scenario: config schema comms.value lists every known :type variant
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.comm.telly {:local/root "modules/isaac.comm.telly"}}}
      """
    When isaac is run with "config schema comms.value"
    Then the stdout matches:
      | pattern          |
      | type:\s+acp      |
      | type:\s+telly    |
      | \[telly\].*loft  |
      | \[telly\].*color |
      | \[telly\].*mood  |
    And the exit code is 0

  Scenario: config schema comms.value with no modules shows only base fields
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {}
      """
    When isaac is run with "config schema comms.value"
    Then the stdout matches:
      | pattern |
      | :type   |
      | :crew   |
    And the stdout does not match:
      | pattern   |
      | \[telly\] |
    And the exit code is 0

  Scenario: config schema for a manifest-supplied field errors when the module isn't declared
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {}
      """
    When isaac is run with "config schema comms.value.loft"
    Then the stderr matches:
      | pattern            |
      | Path not found     |
      | comms\.value\.loft |
    And the exit code is 1

  Scenario: config schema renders manifest-supplied provider fields with provenance prefix
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.providers.kombucha {:local/root "modules/isaac.providers.kombucha"}}}
      """
    When isaac is run with "config schema providers.value.fizz-level"
    Then the stdout matches:
      | pattern      |
      | \[kombucha\] |
      | type:\s+int  |
    And the exit code is 0

  Scenario: config schema renders manifest-supplied tool fields with provenance prefix
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {}
      """
    When isaac is run with "config schema tools.web_search.api-key"
    Then the stdout matches:
      | pattern        |
      | \[web_search\] |
      | type:\s+string |
    And the exit code is 0

  Scenario: config schema renders manifest-supplied slash-command fields with provenance prefix
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}}
      """
    When isaac is run with "config schema slash-commands.echo.command-name"
    Then the stdout matches:
      | pattern        |
      | \[echo\]       |
      | type:\s+string |
    And the exit code is 0
