@wip
Feature: OpenAI Responses API surface
  Wire-shape tests for the openai-responses API. Effort integers on
  the request map are translated to OpenAI's low|medium|high enum on
  the nested reasoning.effort field.

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

  Scenario Outline: Effort integer maps to nested reasoning.effort
    Given the isaac EDN file "config/crew/oscar.edn" exists with:
      | path   | value                 |
      | model  | snuffy                |
      | soul   | Lives in a trash can. |
      | effort | <effort>              |
    When the user sends "hi" on session "trash-can"
    Then the last outbound HTTP request matches:
      | key                   | value  |
      | body.reasoning.effort | <wire> |

    Examples:
      | effort | wire   | #comment          |
      | 0      |        | omitted entirely  |
      | 1      | low    | low band start    |
      | 3      | low    | low band end      |
      | 4      | medium | medium band start |
      | 6      | medium | medium band end   |
      | 7      | high   | high band start   |
      | 10     | high   | high band end     |
