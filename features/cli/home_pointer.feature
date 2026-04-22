Feature: Home-pointer config file
  Isaac locates its home directory via a lookup chain. A pointer file
  at ~/.config/isaac.edn (or ~/.isaac.edn as fallback) can specify
  an alternate home, avoiding --home on every command.

  Lookup order (first hit wins):
    1. --home CLI flag
    2. ~/.config/isaac.edn with {:home "/path"}
    3. ~/.isaac.edn with {:home "/path"}
    4. ~/.isaac/ (built-in default)

  Background:
    Given the user home directory is "/tmp/user"

  @wip
  Scenario: Isaac reads its home from ~/.config/isaac.edn
    Given a file "/tmp/user/.config/isaac.edn" exists with content "{:home \"/tmp/elsewhere\"}"
    And config file "/tmp/elsewhere/config/isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config sources"
    Then the output contains "/tmp/elsewhere"
    And the exit code is 0

  @wip
  Scenario: --home flag overrides the pointer file
    Given a file "/tmp/user/.config/isaac.edn" exists with content "{:home \"/tmp/pointer-path\"}"
    And config file "/tmp/flag-path/config/isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "--home /tmp/flag-path config sources"
    Then the output contains "/tmp/flag-path"
    And the output does not contain "/tmp/pointer-path"
    And the exit code is 0
