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
      | pattern                                               |
      | Usage: isaac config \[subcommand\] \[options\]        |
      | Manage Isaac configuration                            |
      | Subcommands:                                          |
      | validate\s+Validate config                            |
      | get <config-path>\s+Get a value by config path        |
      | sources\s+List contributing config files              |
      | Options:                                              |
      | --raw\s+Print pre-substitution config                 |
    And the exit code is 0

  Scenario: config validate has its own help page via --help
    When isaac is run with "config validate --help"
    Then the output matches:
      | pattern                                                  |
      | Usage: isaac config validate \[options\] \[-\]           |
      | Validate the config composition                          |
      | Options:                                                 |
      | --as <config-path>\s+Overlay stdin EDN                   |
      | Arguments:                                               |
      | -\s+Read EDN to validate from stdin                      |
    And the exit code is 0

  Scenario: config help validate is an alternate way to reach subcommand help
    When isaac is run with "config help validate"
    Then the output matches:
      | pattern                                                  |
      | Usage: isaac config validate \[options\] \[-\]           |
      | Validate the config composition                          |
    And the exit code is 0

  # ----- Print (default / --raw / --reveal) -----

  Scenario: config redacts resolved ${VAR} values by default
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
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
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
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
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is:
      """
      REVEAL
      """
    When isaac is run with "config --reveal"
    Then the stderr contains "type REVEAL to confirm:"
    And the output lines contain in order:
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
    Then the stderr contains "type REVEAL to confirm:"
    And the stderr contains "Refusing to reveal config."
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
       :crew     {:main {:soul "You are Isaac."}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
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
       :models       {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers    {:anthropic {}}
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

  Scenario: validate reads stdin as the full config and ignores on-disk files
    Given config file "isaac.edn" containing:
      """
      {:broken-key-that-should-error true}
      """
    And stdin is:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config validate -"
    Then the output contains "valid"
    And the exit code is 0

  Scenario: validate --as overlays stdin at the given config path before validating
    Given config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    And stdin is:
      """
      {:soul "A paranoid android."}
      """
    When isaac is run with "config validate --as crew.main -"
    Then the output contains "valid"
    And the exit code is 0

  Scenario: validate --as rejects file-path style with a hint to use a config path
    Given stdin is:
      """
      {:soul "..."}
      """
    When isaac is run with "config validate --as crew/marvin.edn -"
    Then the stderr contains "config path"
    And the exit code is 1

  # ----- Get -----

  Scenario: get prints a scalar value by dotted keyword path
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}
                  :marvin {:soul "You are Marvin."}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config get crew.marvin.soul"
    Then the output contains "You are Marvin."
    And the exit code is 0

  Scenario: get prints a scalar value by bracket keyword path
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}
                  :marvin {:soul "You are Marvin."}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config get crew[:marvin].soul"
    Then the output contains "You are Marvin."
    And the exit code is 0

  Scenario: get prints a nested structure as EDN
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}
                  :marvin {:model :llama :soul "You are Marvin."}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
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
       :crew     {:main {}
                  :marvin {:soul "You are Marvin."}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config get crew.marvin.nope"
    Then the stderr contains "not found: crew.marvin.nope"
    And the exit code is 1

  Scenario: get redacts resolved ${VAR} values by default
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
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
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is:
      """
      REVEAL
      """
    When isaac is run with "config get providers.anthropic.api-key --reveal"
    Then the stderr contains "type REVEAL to confirm:"
    And the output contains "sk-test-123"
    And the exit code is 0

  Scenario: get --reveal refuses on invalid confirmation
    Given environment variable "CONFIG_TEST_API_KEY" is "sk-test-123"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :crew      {:main {}}
       :models    {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {:api-key "${CONFIG_TEST_API_KEY}"}}}
      """
    And stdin is:
      """
      blah
      """
    When isaac is run with "config get providers --reveal"
    Then the stderr contains "type REVEAL to confirm:"
    And the stderr contains "Refusing to reveal config."
    And the output does not contain "sk-test-123"
    And the exit code is 1

  # ----- Schema -----

  Scenario: config schema prints the root schema with title, fields, and guidance
    When isaac is run with "config schema"
    Then the output matches:
      | pattern                                 |
      | \[isaac\] isaac schema                  |
      | crew\s+.*\[crew\]                       |
      | defaults\s+.*\[defaults\]               |
      | models\s+.*\[models\]                   |
      | providers\s+.*\[providers\]             |
      | Try:                                    |
      | isaac config schema crew                |
      | isaac config schema providers\.value    |
      | isaac config schema crew\.value\.model  |
    And the exit code is 0

  Scenario: config schema --tree expands every named sub-schema
    When isaac is run with "config schema --tree"
    Then the output matches:
      | pattern                                           |
      | \[isaac\] isaac schema                            |
      | \[crew\.value\] crew schema                       |
      | Crew member id; must match filename when present  |
      | \[providers\.value\] provider schema              |
      | base-url                                          |
    And the exit code is 0

  Scenario: config schema crew renders the map wrapper with key/value rows
    When isaac is run with "config schema crew"
    Then the output matches:
      | pattern                                       |
      | \[crew\] crew table schema                    |
      | map of                                        |
      | key\s+string\s+\[crew\.key\]               |
      | value\s+.*crew\s+\[crew\.value\]                |
      | Crew member configurations                    |
    And the output does not contain "Model alias"
    And the output does not contain "System prompt"
    And the exit code is 0

  Scenario: config schema crew.value prints the crew entity fields
    When isaac is run with "config schema crew.value"
    Then the output matches:
      | pattern                                           |
      | \[crew\.value\] crew schema                          |
      | model\s+string\s+\[crew\.value\.model\]               |
      | soul\s+string\s+\[crew\.value\.soul\]                 |
    And the exit code is 0

  Scenario: config schema providers.key resolves the map-key spec
    When isaac is run with "config schema providers.key"
    Then the output matches:
      | pattern                          |
      | \[providers\.key\] schema       |
      | string\s+\[providers\.key\]     |
    And the exit code is 0

  Scenario: config schema providers.value prints the provider entity template
    When isaac is run with "config schema providers.value"
    Then the output matches:
      | pattern                                           |
      | \[providers\.value\] provider schema                  |
      | api-key\s+string\s+\[providers\.value\.api-key\]      |
      | base-url\s+string\s+\[providers\.value\.base-url\]    |
    And the exit code is 0

  Scenario: config schema crew.value.id prints the :id field schema
    When isaac is run with "config schema crew.value.id"
    Then the output matches:
      | pattern                                           |
      | \[crew\.value\.id\] schema                            |
      | string\s+\[crew\.value\.id\]                          |
      | Crew member id; must match filename when present  |
    And the exit code is 0

  Scenario: config schema drills into a single field
    When isaac is run with "config schema providers.value.api-key"
    Then the output matches:
      | pattern                                       |
      | \[providers\.value\.api-key\] schema              |
      | string\s+\[providers\.value\.api-key\]            |
      | API key                                       |
    And the exit code is 0

  Scenario: config schema gives a friendly error for an invalid path
    When isaac is run with "config schema providers.valued"
    Then the stderr contains "Path not found in config schema: providers.valued"
    And the stderr does not contain "Exception"
    And the exit code is 1

  Scenario: config help lists the schema subcommand
    When isaac is run with "help config"
    Then the output matches:
      | pattern                                               |
      | schema \[schema-path\]\s+Print the config schema      |
    And the exit code is 0

  Scenario: config schema --help describes the --tree flag
    When isaac is run with "config schema --help"
    Then the output matches:
      | pattern                              |
      | Usage: isaac config schema           |
      | --tree\s+Expand every named           |
    And the exit code is 0

  # ----- Set -----

  Scenario: set writes a new crew member to isaac.edn by default
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}
                  :gpt   {:model "gpt-5.4" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config set crew.marvin.model gpt"
    Then the config file "isaac.edn" matches:
      | pattern       |
      | :marvin       |
      | :model\s+:gpt |
    And the log has entries matching:
      | level | event       | path              | value | file       |
      | :info | :config/set | crew.marvin.model | :gpt  | isaac.edn  |
    And the exit code is 0

  Scenario: set writes to the existing entity file when one already defines the key
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}
                  :gpt   {:model "gpt-5.4" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    And config file "crew/marvin.edn" containing:
      """
      {:model :llama}
      """
    When isaac is run with "config set crew.marvin.model gpt"
    Then the config file "crew/marvin.edn" matches:
      | pattern       |
      | :model\s+:gpt |
    And the config file "isaac.edn" does not contain "marvin"
    And the log has entries matching:
      | level | event       | path              | value | file            |
      | :info | :config/set | crew.marvin.model | :gpt  | crew/marvin.edn |
    And the exit code is 0

  Scenario: set writes to isaac.edn when the entity is already defined there
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main   {}
                  :marvin {:model :llama}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}
                  :gpt   {:model "gpt-5.4" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config set crew.marvin.model gpt"
    Then the config file "isaac.edn" matches:
      | pattern       |
      | :marvin       |
      | :model\s+:gpt |
    And the config file "crew/marvin.edn" does not exist
    And the log has entries matching:
      | level | event       | path              | value | file      |
      | :info | :config/set | crew.marvin.model | :gpt  | isaac.edn |
    And the exit code is 0

  Scenario: set writes new entities to entity files when prefer-entity-files is true
    Given config file "isaac.edn" containing:
      """
      {:defaults            {:crew :main :model :llama}
       :prefer-entity-files true
       :crew                {:main {}}
       :models              {:llama {:model "llama3.3:1b" :provider :anthropic}
                             :gpt   {:model "gpt-5.4" :provider :anthropic}}
       :providers           {:anthropic {}}}
      """
    When isaac is run with "config set crew.marvin.model gpt"
    Then the config file "crew/marvin.edn" matches:
      | pattern       |
      | :model\s+:gpt |
    And the config file "isaac.edn" does not contain "marvin"
    And the log has entries matching:
      | level | event       | path              | value | file            |
      | :info | :config/set | crew.marvin.model | :gpt  | crew/marvin.edn |
    And the exit code is 0

  Scenario: set writes soul to the companion .md when it already exists
    Given config file "models/gpt.edn" containing:
      """
      {:model "gpt-5.4" :provider :anthropic}
      """
    And config file "providers/anthropic.edn" containing:
      """
      {}
      """
    Given config file "crew/marvin.edn" containing:
      """
      {:model :gpt}
      """
    And config file "crew/marvin.md" containing:
      """
      Old soul.
      """
    When isaac is run with "config set crew.marvin.soul \"New soul.\""
    Then the config file "crew/marvin.md" matches:
      | pattern   |
      | New soul. |
    And the config file "crew/marvin.edn" does not contain ":soul"
    And the log has entries matching:
      | level | event       | path             | value     | file           |
      | :info | :config/set | crew.marvin.soul | New soul. | crew/marvin.md |
    And the exit code is 0

  Scenario: set creates a companion .md when a new soul exceeds 64 characters
    Given config file "models/gpt.edn" containing:
      """
      {:model "gpt-5.4" :provider :anthropic}
      """
    And config file "providers/anthropic.edn" containing:
      """
      {}
      """
    Given config file "crew/marvin.edn" containing:
      """
      {:model :gpt}
      """
    When isaac is run with "config set crew.marvin.soul \"You are Marvin, the paranoid android from Hitchhiker's Guide to the Galaxy. Depression is your primary mood.\""
    Then the config file "crew/marvin.md" matches:
      | pattern            |
      | You are Marvin     |
      | Hitchhiker's Guide |
    And the config file "crew/marvin.edn" does not contain ":soul"
    And the log has entries matching:
      | level | event       | path             | value                                                                                                            | file           |
      | :info | :config/set | crew.marvin.soul | You are Marvin, the paranoid android from Hitchhiker's Guide to the Galaxy. Depression is your primary mood.    | crew/marvin.md |
    And the exit code is 0

  Scenario: set writes short soul inline in the entity file
    Given config file "models/gpt.edn" containing:
      """
      {:model "gpt-5.4" :provider :anthropic}
      """
    And config file "providers/anthropic.edn" containing:
      """
      {}
      """
    Given config file "crew/marvin.edn" containing:
      """
      {:model :gpt}
      """
    When isaac is run with "config set crew.marvin.soul \"Paranoid android.\""
    Then the config file "crew/marvin.edn" matches:
      | pattern                      |
      | :soul\s+"Paranoid android\." |
    And the config file "crew/marvin.md" does not exist
    And the log has entries matching:
      | level | event       | path             | value              | file            |
      | :info | :config/set | crew.marvin.soul | Paranoid android. | crew/marvin.edn |
    And the exit code is 0

  Scenario: set refuses to write a value that fails validation
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config set crew.marvin.model nonexistent-model"
    Then the stderr matches:
      | pattern                    |
      | references undefined model |
    And the config file "isaac.edn" does not contain "nonexistent-model"
    And the log has entries matching:
      | level  | event              | path              | error                           |
      | :error | :config/set-failed | crew.marvin.model | #".*references undefined model.*" |
    And the exit code is 1

  Scenario: set on an unknown key warns but still writes
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    When isaac is run with "config set crew.main.experimental true"
    Then the stderr matches:
      | pattern        |
      | warning        |
      | crew\.main\.experimental |
      | unknown key    |
    And the config file "isaac.edn" matches:
      | pattern              |
      | :experimental\s+true |
    And the log has entries matching:
      | level | event       | path                  | value | file      |
      | :info | :config/set | crew.main.experimental | true  | isaac.edn |
    And the exit code is 0

  # ----- Unset -----

  Scenario: unset removes a key from the file where it lives
    Given config file "models/gpt.edn" containing:
      """
      {:model "gpt-5.4" :provider :anthropic}
      """
    And config file "providers/anthropic.edn" containing:
      """
      {}
      """
    Given config file "crew/marvin.edn" containing:
      """
      {:model :gpt :soul "Paranoid."}
      """
    When isaac is run with "config unset crew.marvin.soul"
    Then the config file "crew/marvin.edn" matches:
      | pattern       |
      | :model\s+:gpt |
    And the config file "crew/marvin.edn" does not contain ":soul"
    And the log has entries matching:
      | level | event         | path             | file            |
      | :info | :config/unset | crew.marvin.soul | crew/marvin.edn |
    And the exit code is 0

  Scenario: unset that empties an entity file deletes it
    Given config file "models/gpt.edn" containing:
      """
      {:model "gpt-5.4" :provider :anthropic}
      """
    And config file "providers/anthropic.edn" containing:
      """
      {}
      """
    Given config file "crew/marvin.edn" containing:
      """
      {:model :gpt}
      """
    When isaac is run with "config unset crew.marvin.model"
    Then the config file "crew/marvin.edn" does not exist
    And the log has entries matching:
      | level | event         | path              | file            |
      | :info | :config/unset | crew.marvin.model | crew/marvin.edn |
    And the exit code is 0

  Scenario: set writes a whole entity read from stdin
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}
       :crew     {:main {}}
       :models   {:llama {:model "llama3.3:1b" :provider :anthropic}}
       :providers {:anthropic {}}}
      """
    And stdin is:
      """
      {:base-url "https://api.x.ai/v1" :api-key "${GROK_API_KEY}" :api "openai-compatible"}
      """
    When isaac is run with "config set providers.grok -"
    Then the config file "isaac.edn" matches:
      | pattern                             |
      | :grok                               |
      | :base-url\s+"https://api\.x\.ai/v1" |
      | :api\s+"openai-compatible"          |
    And the log has entries matching:
      | level | event       | path           | value              | file      |
      | :info | :config/set | providers.grok | #".*api\.x\.ai/v1.*" | isaac.edn |
    And the exit code is 0

  Scenario: set replaces an existing entity rather than merging
    Given config file "providers/grok.edn" containing:
      """
      {:base-url "https://old.example.com" :api-key "${OLD_KEY}" :api "openai-compatible"}
      """
    And stdin is:
      """
      {:base-url "https://api.x.ai/v1" :api-key "${GROK_API_KEY}"}
      """
    When isaac is run with "config set providers.grok -"
    Then the config file "providers/grok.edn" matches:
      | pattern                             |
      | :base-url\s+"https://api\.x\.ai/v1" |
      | :api-key\s+"\$\{GROK_API_KEY\}"     |
    And the config file "providers/grok.edn" does not contain "old.example.com"
    And the config file "providers/grok.edn" does not contain ":api "
    And the log has entries matching:
      | level | event       | path           | value                  | file               |
      | :info | :config/set | providers.grok | #".*\$\{GROK_API_KEY\}.*" | providers/grok.edn |
    And the exit code is 0

  Scenario: config help lists set and unset subcommands
    When isaac is run with "help config"
    Then the output matches:
      | pattern                                                 |
      | set <config-path> <value>\s+Set a value at a config path |
      | unset <config-path>\s+Remove a value at a config path    |
    And the exit code is 0

  Scenario: config set --help documents stdin form and examples
    When isaac is run with "config set --help"
    Then the output matches:
      | pattern                                       |
      | Usage: isaac config set <config-path>         |
      | -\s+Read the value as EDN from stdin          |
    And the exit code is 0
