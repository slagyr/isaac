@wip
Feature: Slash Cmds
  The bridge intercepts input starting with / and handles it
  locally without sending it to the LLM. Unrecognized slash-cmds
  produce a helpful error.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And agent "main" has sessions:
      | key                         | totalTokens | compactionCount |
      | agent:main:cli:direct:user1 | 5000        | 2               |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | hi              |
      | message | user         | how are you     |
      | message | assistant    | fine            |
    And the built-in tools are registered

  Scenario: /status prints session information
    When the user sends "/status" on session "agent:main:cli:direct:user1"
    Then the output matches:
      | pattern                     |
      | agent: main                 |
      | provider: grover            |
      | model: echo                 |
      | context window: 32,768      |
      | tokens: 5,000               |
      | 15%                         |
      | agent:main:cli:direct:user1 |
      | \.jsonl                     |
      | turns: 4                    |
      | compactions: 2              |
      | SOUL\.md\|You are Isaac     |
      | tools: \d+                  |
      | cwd:                        |

  Scenario: /status is not sent to the LLM
    When the user sends "/status" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | hi              |
      | message | user         | how are you     |
      | message | assistant    | fine            |

  Scenario: unrecognized slash command produces an error
    When the user sends "/bogus" on session "agent:main:cli:direct:user1"
    Then the output contains "unknown command: /bogus"

  Scenario: normal input is not intercepted by the bridge
    Given the following model responses are queued:
      | type | content      | model |
      | text | I am fine    | echo  |
    When the user sends "how are you?" on session "agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.content |
      | message | assistant    | I am fine       |
