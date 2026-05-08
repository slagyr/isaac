Feature: Provider extension
  Providers are first-class extensions distinct from Apis. An Api is a
  wire-format adapter (anthropic-messages, openai-completions, etc.) that
  ships as Clojure code. A Provider is configuration data pointing at one
  Api with a base-url, auth mode, and model list. xAI is a Provider that
  uses the openai-completions Api; Anthropic-via-corp-gateway is a
  Provider that inherits from the built-in :anthropic with an overridden
  base-url.

  Three contribution paths converge in the provider registry:
  - Built-in providers registered at startup (anthropic, openai-codex, ...)
  - Module-declared providers (manifest-only, no Clojure code required)
  - User-declared providers inline in isaac.edn

  All three are uniform sources for :from inheritance.

  @wip
  Scenario: A user-declared provider is usable for a turn
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:xai {:api      "openai-completions"
                         :base-url "https://api.x.ai/v1"
                         :auth     "api-key"
                         :api-key  "xoxo-test-key"
                         :models   ["grok-2"]}}
       :crew      {:main {:provider :xai :model "grok-2"}}}
      """
    When the user sends "Hello, Grok" on session "main" via memory comm
    Then the last outbound HTTP request matches:
      | key                   | value                                |
      | url                   | https://api.x.ai/v1/chat/completions |
      | headers.Authorization | Bearer xoxo-test-key                 |
      | body.model            | grok-2                               |

  @wip
  Scenario: A provider inherits defaults from another via :from
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:corp-anthropic {:from     :anthropic
                                    :base-url "https://anthropic.internal.corp"
                                    :api-key  "corp-secret-99"}}
       :crew      {:main {:provider :corp-anthropic :model "claude-sonnet-4-6"}}}
      """
    When the user sends "ping" on session "main" via memory comm
    Then the last outbound HTTP request matches:
      | key               | value                                       |
      | url               | https://anthropic.internal.corp/v1/messages |
      | headers.x-api-key | corp-secret-99                              |
      | body.model        | claude-sonnet-4-6                           |

  @wip
  Scenario: A module-declared provider is usable without any module code
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules   {:isaac.providers.kombucha {:local/root "modules/isaac.providers.kombucha"}}
       :providers {:kombucha {:api-key "fizzy-secret"}}
       :crew      {:main {:provider :kombucha :model "kombucha-large"}}}
      """
    When the user sends "what flavor today" on session "main" via memory comm
    Then the last outbound HTTP request matches:
      | key                   | value                                         |
      | url                   | https://api.kombucha.test/v1/chat/completions |
      | headers.Authorization | Bearer fizzy-secret                           |
      | body.model            | kombucha-large                                |

  @wip
  Scenario: A user-defined provider can inherit from a module-declared provider
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules   {:isaac.providers.kombucha {:local/root "modules/isaac.providers.kombucha"}}
       :providers {:fizzy-staging {:from    :kombucha
                                   :api-key "staging-key"}}
       :crew      {:main {:provider :fizzy-staging :model "kombucha-small"}}}
      """
    When the user sends "ping" on session "main" via memory comm
    Then the last outbound HTTP request matches:
      | key                   | value                                         |
      | url                   | https://api.kombucha.test/v1/chat/completions |
      | headers.Authorization | Bearer staging-key                            |
      | body.model            | kombucha-small                                |

  @wip
  Scenario: A provider with an unknown :api is rejected at config-load
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:bogus {:api      "carrier-pigeon"
                           :base-url "https://example.com"
                           :auth     "api-key"
                           :api-key  "test"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                 | value       |
      | providers.bogus.api | unknown api |

  @wip
  Scenario: A provider with an unknown :from target is rejected
    Given an empty Isaac state directory "/tmp/isaac"
    And the isaac file "isaac.edn" exists with:
      """
      {:providers {:dreamy {:from    :ghost-provider
                            :api-key "test"}}}
      """
    When the config is loaded
    Then the config has validation errors matching:
      | key                   | value            |
      | providers.dreamy.from | unknown provider |
