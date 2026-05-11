@wip
Feature: Ollama API surface
  Wire-shape tests for the ollama API. Effort integers on the request
  map are translated to the body.think field. Default :bool mode
  collapses to think true/false (universal across thinking-capable
  Ollama models). Opt-in :levels mode buckets to "low"|"medium"|"high"
  for models that accept tier strings.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |
    And the isaac EDN file "config/models/qwen3.edn" exists with:
      | path           | value         |
      | model          | qwen3         |
      | provider       | grover:ollama |
      | context-window | 32768         |
    And the isaac EDN file "config/crew/qwerty.edn" exists with:
      | path  | value           |
      | model | qwen3           |
      | soul  | Thinks in QWEN. |
    And the following sessions exist:
      | name       | crew   |
      | qwen-house | qwerty |
    And the following model responses are queued:
      | model | type | content |
      | qwen3 | text | ok      |

  Scenario Outline: Default :bool mode collapses effort to think true/false
    Given the isaac EDN file "config/crew/qwerty.edn" exists with:
      | path   | value           |
      | model  | qwen3           |
      | soul   | Thinks in QWEN. |
      | effort | <effort>        |
    When the user sends "hi" on session "qwen-house"
    Then the last outbound HTTP request matches:
      | key        | value   |
      | body.think | <think> |

    Examples:
      | effort | think | #comment               |
      | 0      | false | thinking off           |
      | 1      | true  | thinking on; tier lost |
      | 2      | true  |                        |
      | 3      | true  |                        |
      | 4      | true  |                        |
      | 5      | true  |                        |
      | 6      | true  |                        |
      | 7      | true  |                        |
      | 8      | true  |                        |
      | 9      | true  |                        |
      | 10     | true  |                        |

  Scenario Outline: :levels mode buckets effort to "low"|"medium"|"high"
    Given the isaac EDN file "config/models/qwen3.edn" exists with:
      | path           | value         |
      | model          | qwen3         |
      | provider       | grover:ollama |
      | context-window | 32768         |
      | think-mode     | levels        |
    And the isaac EDN file "config/crew/qwerty.edn" exists with:
      | path   | value           |
      | model  | qwen3           |
      | soul   | Thinks in QWEN. |
      | effort | <effort>        |
    When the user sends "hi" on session "qwen-house"
    Then the last outbound HTTP request matches:
      | key        | value   |
      | body.think | <think> |

    Examples:
      | effort | think  | #comment            |
      | 0      |        | think field omitted |
      | 1      | low    | low band start      |
      | 2      | low    |                     |
      | 3      | low    | low band end        |
      | 4      | medium | medium band start   |
      | 5      | medium |                     |
      | 6      | medium | medium band end     |
      | 7      | high   | high band start     |
      | 8      | high   |                     |
      | 9      | high   |                     |
      | 10     | high   | high band end       |
