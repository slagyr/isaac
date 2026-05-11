@wip
Feature: Per-turn token usage normalization
  Every assistant transcript entry carries a single normalized :usage
  block describing the tokens that turn consumed. The block is always
  present, accumulates across tool-loop sub-calls, and uses one
  consistent key shape regardless of provider.

  Background:
    Given default Grover setup
    And the following sessions exist:
      | name       |
      | usage-test |

  Scenario: Input and output tokens are normalized
    Given the following model responses are queued:
      | model | type | content   | usage.input_tokens | usage.output_tokens |
      | echo  | text | Hi there  | 100                | 25                  |
    When the user sends "hello" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.usage.input-tokens | message.usage.output-tokens | message.usage.total-tokens |
      | assistant    | 100                        | 25                          | 125                        |

  Scenario: Cache-read tokens are normalized
    Given the following model responses are queued:
      | model | type | content  | usage.input_tokens | usage.output_tokens | usage.input_tokens_details.cached_tokens |
      | echo  | text | Hi there | 200                | 30                  | 50                                       |
    When the user sends "hello" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.usage.cache-read |
      | assistant    | 50                       |

  Scenario: Cache-write tokens are normalized
    Given the following model responses are queued:
      | model | type | content  | usage.input_tokens | usage.output_tokens | usage.cache_creation_input_tokens |
      | echo  | text | Hi there | 200                | 30                  | 75                                |
    When the user sends "hello" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.usage.cache-write |
      | assistant    | 75                        |

  Scenario: Reasoning tokens are normalized
    Given the following model responses are queued:
      | model | type | content  | usage.input_tokens | usage.output_tokens | usage.output_tokens_details.reasoning_tokens |
      | echo  | text | Hi there | 100                | 25                  | 200                                          |
    When the user sends "hello" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.usage.reasoning-tokens |
      | assistant    | 200                            |

  Scenario: Usage defaults to zeros when the provider reports nothing
    Given the following model responses are queued:
      | model | type | content   |
      | echo  | text | Hi there  |
    When the user sends "hello" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.usage.input-tokens | message.usage.output-tokens | message.usage.total-tokens |
      | assistant    | 0                          | 0                           | 0                          |

  Scenario: Usage accumulates across a tool loop
    Given the following model responses are queued:
      | model | type      | tool_call | arguments      | content    | usage.input_tokens | usage.output_tokens |
      | echo  | tool_call | read      | {"path":"a"}   |            | 50                 | 10                  |
      | echo  | text      |           |                | All done   | 80                 | 20                  |
    When the user sends "read it" on session "usage-test"
    Then session "usage-test" has transcript matching:
      | message.role | message.content | message.usage.input-tokens | message.usage.output-tokens | message.usage.total-tokens |
      | assistant    | All done        | 130                        | 30                          | 160                        |
