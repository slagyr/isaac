@wip
Feature: OpenAI Chat-Completions API surface
  Wire-shape tests for the openai-completions API. Effort integers
  on the request map are translated to OpenAI's low|medium|high enum
  on the top-level reasoning_effort field.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |
    And the isaac EDN file "config/models/g5.edn" exists with:
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

  Scenario Outline: Effort integer maps to top-level reasoning_effort
    Given the isaac EDN file "config/crew/thinker.edn" exists with:
      | path   | value       |
      | model  | g5          |
      | soul   | Think hard. |
      | effort | <effort>    |
    When the user sends "n queens?" on session "desk"
    Then the last outbound HTTP request matches:
      | key                   | value  |
      | body.reasoning_effort | <wire> |

    Examples:
      | effort | wire   | #comment          |
      | 0      |        | omitted entirely  |
      | 1      | low    | low band start    |
      | 3      | low    | low band end      |
      | 4      | medium | medium band start |
      | 6      | medium | medium band end   |
      | 7      | high   | high band start   |
      | 10     | high   | high band end     |
