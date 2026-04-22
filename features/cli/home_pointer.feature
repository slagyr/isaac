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

  Scenario: Isaac reads its home from ~/.config/isaac.edn
    Given the file "/tmp/user/.config/isaac.edn" exists with:
      """
      {:home "/tmp/elsewhere"}
      """
    And the file "/tmp/elsewhere/.isaac/config/isaac.edn" exists with:
      """
      {:defaults {:crew :main :model :llama}
       :crew {:main {}}
       :models {:llama {:model "llama" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config get defaults.model"
    Then the output contains "llama"
    And the exit code is 0

  Scenario: --home flag overrides the pointer file
    Given the file "/tmp/user/.config/isaac.edn" exists with:
      """
      {:home "/tmp/pointer-path"}
      """
    And the file "/tmp/pointer-path/.isaac/config/isaac.edn" exists with:
      """
      {:defaults {:crew :main :model :pointer}
       :crew {:main {}}
       :models {:pointer {:model "pointer" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    And the file "/tmp/flag-path/.isaac/config/isaac.edn" exists with:
      """
      {:defaults {:crew :main :model :flag}
       :crew {:main {}}
       :models {:flag {:model "flag" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "--home /tmp/flag-path config get defaults.model"
    Then the output contains "flag"
    And the output does not contain "pointer"
    And the exit code is 0
