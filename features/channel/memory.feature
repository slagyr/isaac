Feature: Memory Channel
  The memory channel records chat events in order without any I/O,
  making it the primary test vehicle for chat flow and forcing a
  clean separation between orchestration and presentation.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |

  @wip
  Scenario: Text response is recorded as a single chunk
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When the user sends "What is 2+2?" on session "agent:main:cli:direct:user1" via memory channel
    Then the memory channel has events matching:
      | event      | text          |
      | turn-start |               |
      | text-chunk | Four, I think |
      | turn-end   |               |

  @wip
  Scenario: Streaming chunks are recorded in order
    Given the following model responses are queued:
      | type | content                           | model |
      | text | ["Once " "upon " "a " "time..."] | echo  |
    When the user sends "Tell me a story" on session "agent:main:cli:direct:user1" via memory channel
    Then the memory channel has events matching:
      | event      | text    |
      | turn-start |         |
      | text-chunk | Once    |
      | text-chunk | upon    |
      | text-chunk | a       |
      | text-chunk | time... |
      | turn-end   |         |

  @wip
  Scenario: Tool calls are recorded as lifecycle events
    Given the built-in tools are registered
    And the following model responses are queued:
      | tool_call | arguments              |
      | exec      | {"command": "echo hi"} |
    When the user sends "Run echo" on session "agent:main:cli:direct:user1" via memory channel
    Then the memory channel has events matching:
      | event       | tool-name |
      | turn-start  |           |
      | tool-call   | exec      |
      | tool-result | exec      |
      | turn-end    |           |

  @wip
  Scenario: Compaction triggers during a memory channel turn
    Given agent "main" has sessions:
      | key                         | totalTokens | #comment              |
      | agent:main:cli:direct:user1 | 30000       | exceeds 90% of 32768  |
    And the following model responses are queued:
      | type | content                | model |
      | text | Summary of prior chat  | echo  |
      | text | Here is my answer      | echo  |
    When the user sends "Continue" on session "agent:main:cli:direct:user1" via memory channel
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type       |
      | compaction |
