Feature: Isaac .env file for ${VAR} substitution
  Operators put API keys and other secrets in ~/.isaac/.env and reference
  them from config via ${VAR} syntax. This file loads as an additional
  env source, layered into c3kit's env precedence below shell env and
  cwd-local .env.

  Background:
    Given an in-memory Isaac state directory "isaac-state"

  @wip
  Scenario: ${VAR} resolves from the isaac .env file
    Given the isaac .env file contains:
      """
      ANTHROPIC_API_KEY=sk-from-isaac
      """
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :llama}
       :providers {:anthropic {:base-url "https://api.anthropic.com"
                               :api     "anthropic"
                               :api-key "${ANTHROPIC_API_KEY}"}}}
      """
    Then the loaded config has:
      | key                         | value         |
      | providers.anthropic.api-key | sk-from-isaac |

  @wip
  Scenario: OS environment variables take precedence over the isaac .env file
    Given environment variable "ANTHROPIC_API_KEY" is "sk-from-os"
    And the isaac .env file contains:
      """
      ANTHROPIC_API_KEY=sk-from-isaac
      """
    And config file "isaac.edn" containing:
      """
      {:providers {:anthropic {:api-key "${ANTHROPIC_API_KEY}"}}}
      """
    Then the loaded config has:
      | key                         | value      |
      | providers.anthropic.api-key | sk-from-os |

  @wip
  Scenario: config loads when the isaac .env file is absent
    Given config file "isaac.edn" containing:
      """
      {:defaults {:crew :main :model :llama}}
      """
    Then the loaded config has:
      | key           | value |
      | defaults.crew | main  |
