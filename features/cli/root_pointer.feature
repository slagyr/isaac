Feature: Root-pointer config file
  Isaac locates its root directory via a lookup chain. A pointer file
  at ~/.config/isaac.edn (or ~/.isaac.edn as fallback) can specify
  an alternate root, avoiding --root on every command.

  Lookup order (first hit wins):
    1. --root CLI flag
    2. --home CLI flag (LEGACY alias, appends /.isaac)
    3. ISAAC_ROOT environment variable
    4. ~/.config/isaac.edn with {:root "/path"}
    5. ~/.isaac.edn with {:root "/path"}
    6. ~/.isaac (built-in default)

  Background:
    Given the user home directory is "/tmp/user"

  Scenario: Isaac reads its root from ~/.config/isaac.edn
    Given the file "/tmp/user/.config/isaac.edn" exists with:
      """
      {:root "/tmp/elsewhere/.isaac"}
      """
    And the file "/tmp/elsewhere/.isaac/config/isaac.edn" exists with:
      """
      {:defaults {:crew :main :model :llama}
       :crew {:main {}}
       :models {:llama {:model "llama" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config get defaults.model"
    Then the stdout contains "llama"
    And the exit code is 0

  Scenario: --root flag overrides the pointer file
    Given the file "/tmp/user/.config/isaac.edn" exists with:
      """
      {:root "/tmp/pointer-path/.isaac"}
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
    When isaac is run with "--root /tmp/flag-path/.isaac config get defaults.model"
    Then the stdout contains "flag"
    And the stdout does not contain "pointer"
    And the exit code is 0
