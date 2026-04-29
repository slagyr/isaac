Feature: Discord client lifecycle
  Isaac connects to Discord on server startup if config is present,
  starts the client when config is added at runtime, stops it when
  config is removed, and does NOT restart when config merely changes
  (the runtime reads fresh cfg per message so updates take effect
  without bouncing the connection).

  Wired through a generic plugin protocol: each comm registers a
  config-path and an on-config-change! handler. The manager diffs
  config at the registered path and emits change events; the plugin
  decides what those events mean.

  Background:
    Given default Grover setup
    And the Discord Gateway is faked in-memory

  @wip
  Scenario: Discord client starts on isaac server startup when config is present
    Given config:
      | key                            | value      |
      | comms.discord.token            | test-token |
      | comms.discord.allow-from.users | ["123"]    |
      | comms.discord.crew             | main       |
    And the Isaac server is running
    Then the Discord client is connected

  @wip
  Scenario: Discord client starts when config is added mid-run
    Given the Isaac server is running
    When the isaac EDN file "config/comms/discord.edn" exists with:
      | path             | value      |
      | token            | test-token |
      | allow-from.users | ["123"]    |
      | crew             | main       |
    Then the log has entries matching:
      | level | event                    | path              |
      | :info | :config/reloaded         | comms/discord.edn |
      | :info | :discord.client/started  |                   |
    And the Discord client is connected

  @wip
  Scenario: Discord client stops when its config is removed mid-run
    Given config:
      | key                            | value      |
      | comms.discord.token            | test-token |
      | comms.discord.allow-from.users | ["123"]    |
      | comms.discord.crew             | main       |
    And the Isaac server is running
    And the Discord client is connected
    When the isaac EDN file "config/comms/discord.edn" is removed
    Then the log has entries matching:
      | level | event                    | path              |
      | :info | :config/reloaded         | comms/discord.edn |
      | :info | :discord.client/stopped  |                   |
    And the Discord client is disconnected

  @wip
  Scenario: Discord client does not restart when its config changes
    Given config:
      | key                            | value      |
      | comms.discord.token            | test-token |
      | comms.discord.allow-from.users | ["123"]    |
      | comms.discord.crew             | main       |
    And the Isaac server is running
    And the Discord client is connected
    When the isaac EDN file "config/comms/discord.edn" exists with:
      | path             | value      |
      | token            | test-token |
      | allow-from.users | ["123"]    |
      | crew             | marvin     |
    Then the log has entries matching:
      | level | event            | path              |
      | :info | :config/reloaded | comms/discord.edn |
    And the log has no entries matching:
      | event                    |
      | :discord.client/started  |
      | :discord.client/stopped  |
    And the Discord client is connected
