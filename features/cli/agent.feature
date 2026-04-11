Feature: Agent single-turn command
  The agent command runs a single turn and exits, mirroring
  openclaw's agent command. Conversations persist across
  invocations via --session (defaults to agent:main:main).

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: Agent command runs one turn and exits
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When isaac is run with "agent -m 'What is 2+2?'"
    Then the output contains "Four, I think"
    And the exit code is 0

  Scenario: Default session is agent:main:main
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "agent -m 'Hi'"
    Then agent "main" has sessions matching:
      | key             |
      | agent:main:main |
    And session "agent:main:main" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | Hi              |
      | message | assistant    | Hello           |

  Scenario: --session resumes an existing session
    Given agent "main" has sessions:
      | key                         |
      | agent:main:cli:direct:user1 |
    And session "agent:main:cli:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
    And the following model responses are queued:
      | type | content | model |
      | text | New one | echo  |
    When isaac is run with "agent -m 'Next' --session agent:main:cli:direct:user1"
    Then session "agent:main:cli:direct:user1" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
      | message | user         | Next            |
      | message | assistant    | New one         |

  Scenario: Missing --message exits non-zero
    When isaac is run with "agent"
    Then the output contains "required"
    And the exit code is 1

  Scenario: --json outputs structured result
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "agent -m 'Hi' --json"
    Then the output contains "response"
    And the output contains "Hello"
    And the exit code is 0

  Scenario: Provider error exits non-zero
    Given the following model responses are queued:
      | type  | content                 | model |
      | error | context length exceeded | echo  |
    When isaac is run with "agent -m 'Hi'"
    Then the exit code is 1
