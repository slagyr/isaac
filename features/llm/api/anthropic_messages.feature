@wip
Feature: Anthropic Messages API surface
  Wire-shape tests for the anthropic-messages API. Effort integers on
  the request map are translated to an integer thinking budget on
  body.thinking.budget_tokens, scaling linearly with the model's
  thinking-budget-max config field. Effort 0 omits the thinking block.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And config:
      | key        | value  |
      | log.output | memory |
    And the isaac EDN file "config/models/claude.edn" exists with:
      | path                | value                     |
      | model               | claude-sonnet-4-5         |
      | provider            | grover:anthropic-messages |
      | context-window      | 200000                    |
      | thinking-budget-max | 32000                     |
    And the isaac EDN file "config/crew/thinker.edn" exists with:
      | path  | value       |
      | model | claude      |
      | soul  | Think hard. |
    And the following sessions exist:
      | name     | crew    |
      | thinking | thinker |
    And the following model responses are queued:
      | model             | type | content |
      | claude-sonnet-4-5 | text | ok      |

  Scenario Outline: Effort integer maps to a thinking budget (linear scale)
    Given the isaac EDN file "config/crew/thinker.edn" exists with:
      | path   | value       |
      | model  | claude      |
      | soul   | Think hard. |
      | effort | <effort>    |
    When the user sends "hi" on session "thinking"
    Then the last outbound HTTP request matches:
      | key                         | value    |
      | body.thinking.type          | <type>   |
      | body.thinking.budget_tokens | <budget> |

    Examples:
      | effort | type    | budget | #comment                    |
      | 0      |         |        | thinking block omitted      |
      | 1      | enabled | 3200   | 10% of max                  |
      | 2      | enabled | 6400   |                             |
      | 3      | enabled | 9600   |                             |
      | 4      | enabled | 12800  |                             |
      | 5      | enabled | 16000  | 50% of max                  |
      | 6      | enabled | 19200  |                             |
      | 7      | enabled | 22400  |                             |
      | 8      | enabled | 25600  |                             |
      | 9      | enabled | 28800  |                             |
      | 10     | enabled | 32000  | 100% of thinking-budget-max |

  Scenario Outline: thinking-budget-max scales the whole curve, not just effort 10
    Given the isaac EDN file "config/models/claude.edn" exists with:
      | path                | value                     |
      | model               | claude-sonnet-4-5         |
      | provider            | grover:anthropic-messages |
      | context-window      | 200000                    |
      | thinking-budget-max | 64000                     |
    And the isaac EDN file "config/crew/thinker.edn" exists with:
      | path   | value       |
      | model  | claude      |
      | soul   | Think hard. |
      | effort | <effort>    |
    When the user sends "hi" on session "thinking"
    Then the last outbound HTTP request matches:
      | key                         | value    |
      | body.thinking.budget_tokens | <budget> |

    Examples:
      | effort | budget | #comment    |
      | 5      | 32000  | 50% of 64k  |
      | 10     | 64000  | 100% of 64k |
