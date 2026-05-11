@wip
Feature: Universal effort abstraction
  Isaac exposes a single integer effort knob (0-10) that applies to
  every model and provider. The universal layer resolves the effort
  value from the configuration chain (session > crew > model >
  provider > defaults.effort > hardcoded 7), respects per-model
  capability via allows-effort, and attaches :effort to the request
  map before any API adapter translates it to a wire shape.

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

  Scenario: Effort defaults to 7 when nothing is configured
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment                           |
      | effort | 7     | Isaac default; pre-API-translation |

  Scenario: Top-level Isaac config overrides the hardcoded default
    Given config:
      | key             | value |
      | defaults.effort | 5     |
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment             |
      | effort | 5     | from defaults.effort |

  Scenario: Provider-level effort overrides the Isaac default
    Given config:
      | key             | value |
      | defaults.effort | 5     |
    And the isaac EDN file "config/providers/grover.edn" exists with:
      | path   | value |
      | effort | 3     |
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment      |
      | effort | 3     | from provider |

  Scenario: Model-level effort overrides provider-level
    Given the isaac EDN file "config/providers/grover.edn" exists with:
      | path   | value |
      | effort | 3     |
    And the isaac EDN file "config/models/snuffy.edn" exists with:
      | path           | value                 |
      | model          | snuffy-codex          |
      | provider       | grover:openai-chatgpt |
      | context-window | 128000                |
      | effort         | 5                     |
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment   |
      | effort | 5     | from model |

  Scenario: Crew-level effort overrides model-level
    Given the isaac EDN file "config/providers/grover.edn" exists with:
      | path   | value |
      | effort | 3     |
    And the isaac EDN file "config/models/snuffy.edn" exists with:
      | path           | value                 |
      | model          | snuffy-codex          |
      | provider       | grover:openai-chatgpt |
      | context-window | 128000                |
      | effort         | 5                     |
    And the isaac EDN file "config/crew/oscar.edn" exists with:
      | path   | value                 |
      | model  | snuffy                |
      | soul   | Lives in a trash can. |
      | effort | 8                     |
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment  |
      | effort | 8     | from crew |

  Scenario: Session-level effort overrides crew-level
    Given the isaac EDN file "config/crew/oscar.edn" exists with:
      | path   | value                 |
      | model  | snuffy                |
      | soul   | Lives in a trash can. |
      | effort | 5                     |
    And the following sessions exist:
      | name       | crew  | effort |
      | think-tank | oscar | 9      |
    When the user sends "hi" on session "think-tank"
    Then the last LLM request matches:
      | key    | value | #comment     |
      | effort | 9     | from session |

  Scenario: Effort 0 is preserved as-is on the request
    Given the isaac EDN file "config/crew/oscar.edn" exists with:
      | path   | value                 |
      | model  | snuffy                |
      | soul   | Lives in a trash can. |
      | effort | 0                     |
    When the user sends "hi" on session "trash-can"
    Then the last LLM request matches:
      | key    | value | #comment                                     |
      | effort | 0     | 0 is a legitimate value; API impl translates |

  Scenario: A model with allows-effort=false omits :effort from the request
    Given the isaac EDN file "config/models/cookie.edn" exists with:
      | path           | value             |
      | model          | cookie            |
      | provider       | grover:openai-api |
      | context-window | 32768             |
      | effort         | 9                 |
      | allows-effort  | false             |
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
    Then the last LLM request matches:
      | key    | value | #comment                                 |
      | effort |       | allows-effort=false: key absent entirely |
