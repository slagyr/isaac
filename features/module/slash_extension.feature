Feature: Slash command extension
  Modules can register slash commands by declaring
  :slash-commands {<name> {:factory ... :schema {...}}} in their
  manifest. Built-in slash commands (status, crew, model, cwd) use the
  same registry, so module-declared and built-in commands coexist in
  available-commands.

  Name collisions are last-wins with a warning. A module can override a
  built-in (intentional enhancement) — the override is logged so it does
  not happen silently.

  Scenario: A module-declared slash command is invokable
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}}
      """
    When the user sends "/echo Hieronymus's emergency lettuce" on session "main" via memory comm
    Then the reply contains "Hieronymus's emergency lettuce"

  Scenario: Slash module activation registers its commands
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log     {:output :memory}
       :modules {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}}
      """
    When the user sends "/echo Hieronymus's emergency lettuce" on session "main" via memory comm
    Then the log has entries matching:
      | level | event             | module           |
      | :info | :module/activated | isaac.slash.echo |
      | :info | :slash/registered | echo             |

  Scenario: Module-declared slash commands appear alongside built-ins after activation
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}}
      """
    When the user sends "/echo Hieronymus's emergency lettuce" on session "main" via memory comm
    Then the available slash commands include:
      | name   | description                   |
      | status | Show session status           |
      | echo   | Echo the input back unchanged |

  Scenario: A configured slash command overrides a built-in
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:log            {:output :memory}
       :modules        {:isaac.slash.echo {:local/root "modules/isaac.slash.echo"}}
       :slash-commands {:echo {:command-name "status"}}}
      """
    When the user sends "/status some text" on session "main" via memory comm
    Then the log has entries matching:
      | level | event           | command |
      | :warn | :slash/override | status  |
    And the reply contains "some text"
