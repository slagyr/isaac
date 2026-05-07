Feature: Reasoning effort plumbing
  Reasoning models — gpt-5 family, codex-*, o-series — generate hidden
  thinking tokens before their visible response. The OpenAI Responses
  API takes a `reasoning.effort` knob (none|low|medium|high), and the
  Chat Completions API takes a top-level `reasoning_effort` for the
  same models. Without it, OpenAI defaults to effort="none" — zero
  reasoning tokens — making Codex / gpt-5 feel measurably dumber than
  the same model through the Codex CLI.

  Isaac's default is high for any OpenAI reasoning model. Provider,
  model, and crew config can override (crew wins, then model, then provider).
  Non-reasoning models never receive the field.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |
    And the isaac EDN file "config/models/snuffy.edn" exists with:
      | path           | value                 |
      | model          | snuffy-codex          |
      | provider       | grover:openai-chatgpt |
      | context-window | 128000                |
    And the isaac EDN file "config/crew/oscar.edn" exists with:
      | path  | value                 |
      | model | snuffy                |
      | soul  | Lives in a trash can. |
    And the following sessions exist:
      | name      | crew  |
      | trash-can | oscar |
    And the following model responses are queued:
      | model        | type | content |
      | snuffy-codex | text | ok      |

  Scenario: Default reasoning effort is high for an OpenAI reasoning model
    When the user sends "hi" on session "trash-can"
    Then the last outbound HTTP request matches:
      | key                   | value |
      | body.reasoning.effort | high  |

  Scenario: Provider-level reasoning-effort overrides the default
    Given the isaac EDN file "config/providers/grover.edn" exists with:
      | path             | value |
      | reasoning-effort | low   |
    When the user sends "hi" on session "trash-can"
    Then the last outbound HTTP request matches:
      | key                   | value |
      | body.reasoning.effort | low   |

  Scenario: Model-level reasoning-effort overrides provider-level
    Given the isaac EDN file "config/providers/grover.edn" exists with:
      | path             | value |
      | reasoning-effort | low   |
    And the isaac EDN file "config/models/snuffy.edn" exists with:
      | path             | value                 |
      | model            | snuffy-codex          |
      | provider         | grover:openai-chatgpt |
      | context-window   | 128000                |
      | reasoning-effort | medium                |
    When the user sends "hi" on session "trash-can"
    Then the last outbound HTTP request matches:
      | key                   | value  |
      | body.reasoning.effort | medium |

  Scenario: Crew-level reasoning-effort overrides model-level and provider-level
    Given the isaac EDN file "config/providers/grover.edn" exists with:
      | path             | value |
      | reasoning-effort | low   |
    And the isaac EDN file "config/models/snuffy.edn" exists with:
      | path             | value                 |
      | model            | snuffy-codex          |
      | provider         | grover:openai-chatgpt |
      | context-window   | 128000                |
      | reasoning-effort | medium                |
    And the isaac EDN file "config/crew/oscar.edn" exists with:
      | path             | value                 |
      | model            | snuffy                |
      | soul             | Lives in a trash can. |
      | reasoning-effort | high                  |
    When the user sends "hi" on session "trash-can"
    Then the last outbound HTTP request matches:
      | key                   | value |
      | body.reasoning.effort | high  |

  Scenario: Non-reasoning Chat-Completions model omits the field even when configured
    Given the isaac EDN file "config/models/cookie.edn" exists with:
      | path             | value             |
      | model            | cookie            |
      | provider         | grover:openai-api |
      | context-window   | 32768             |
      | reasoning-effort | high              |
    And the isaac EDN file "config/crew/cmonster.edn" exists with:
      | path  | value           |
      | model | cookie          |
      | soul  | Me love cookie! |
    And the following sessions exist:
      | name       | crew     |
      | cookie-jar | cmonster |
    And the following model responses are queued:
      | model  | type | content |
      | cookie | text | ok      |
    When the user sends "hi" on session "cookie-jar"
    Then the last outbound HTTP request matches:
      | key                   | value |
      | body.reasoning_effort |       |
      | body.reasoning        |       |

  Scenario: Reasoning model on Chat-Completions sends top-level reasoning_effort
    Given the isaac EDN file "config/models/g5.edn" exists with:
      | path           | value             |
      | model          | gpt-5             |
      | provider       | grover:openai-api |
      | context-window | 128000            |
    And the isaac EDN file "config/crew/thinker.edn" exists with:
      | path  | value       |
      | model | g5          |
      | soul  | Think hard. |
    And the following sessions exist:
      | name | crew    |
      | desk | thinker |
    And the following model responses are queued:
      | model | type | content |
      | gpt-5 | text | ok      |
    When the user sends "n queens?" on session "desk"
    Then the last outbound HTTP request matches:
      | key                   | value |
      | body.reasoning_effort | high  |

  Scenario: Response usage and reasoning are logged for diagnostics
    When the user sends "hi" on session "trash-can"
    Then the log has entries matching:
      | level | event                          | model        | reasoning.effort | usage.output_tokens_details.reasoning_tokens |
      | :info | :openai-compat/responses-usage | snuffy-codex | high             | #"\d+"                                       |
