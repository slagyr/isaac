Feature: Sessions Command
  `isaac sessions` lists stored conversation sessions, grouped
  by agent. Use --agent to filter to a single agent.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |

  Scenario: sessions is registered and has help
    When isaac is run with "help sessions"
    Then the output contains "Usage: isaac sessions"
    And the exit code is 0

  Scenario: sessions lists all sessions grouped by agent
    Given agent "main" has sessions:
      | key                            | totalTokens | updatedAt           |
      | agent:main:acp:direct:abc      | 5000        | 2026-04-12T15:00:00 |
      | agent:main:acp:direct:def      | 778         | 2026-04-12T10:00:00 |
    And agent "ketch" has sessions:
      | key                             | totalTokens | updatedAt           |
      | agent:ketch:acp:direct:ghi      | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions"
    Then the output matches:
      | pattern                    |
      | agent: main                |
      | agent:main:acp:direct:abc  |
      | agent:main:acp:direct:def  |
      | agent: ketch               |
      | agent:ketch:acp:direct:ghi |
    And the exit code is 0

  Scenario: sessions --agent filters to one agent
    Given agent "main" has sessions:
      | key                            | totalTokens | updatedAt           |
      | agent:main:acp:direct:abc      | 5000        | 2026-04-12T15:00:00 |
    And agent "ketch" has sessions:
      | key                             | totalTokens | updatedAt           |
      | agent:ketch:acp:direct:ghi      | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions --agent ketch"
    Then the output matches:
      | pattern                          |
      | agent: ketch                     |
      | agent:ketch:acp:direct:ghi       |
    And the output does not contain "agent: main"
    And the exit code is 0

  Scenario: sessions with no sessions prints a message
    When isaac is run with "sessions"
    Then the output contains "no sessions"
    And the exit code is 0

  Scenario: sessions --agent with unknown agent prints an error
    When isaac is run with "sessions --agent nonexistent"
    Then the stderr contains "unknown agent"
    And the stderr contains "nonexistent"
    And the exit code is 1
