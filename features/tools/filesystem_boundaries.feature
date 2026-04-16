@wip
Feature: Per-crew filesystem boundaries
  Each crew member can only access their workspace and
  explicitly whitelisted directories. File operations
  outside these boundaries are rejected.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |

  Scenario: crew member can read files in their workspace
    Given the following crew exist:
      | name | soul           | model  | tools.allow |
      | main | You are Isaac. | grover | read        |
    And the following sessions exist:
      | name       |
      | fence-test |
    And crew workspace "main" contains file "notes.txt" with content "hello"
    And the following model responses are queued:
      | model | tool_call | arguments                                    |
      | echo  | read      | {"filePath": "<main-workspace>/notes.txt"}    |
      | model | type      | content                                      |
      | echo  | text      | Got it                                       |
    When the user sends "read notes" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |

  Scenario: crew member can read files in whitelisted directories
    Given the following crew exist:
      | name | soul           | model  | tools.allow | tools.directories     |
      | main | You are Isaac. | grover | read        | /tmp/isaac-playground |
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | model | tool_call | arguments                                      |
      | echo  | read      | {"filePath": "/tmp/isaac-playground/data.txt"}  |
      | model | type      | content                                        |
      | echo  | text      | Got it                                         |
    When the user sends "read data" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |

  Scenario: crew member cannot read files outside boundaries
    Given the following crew exist:
      | name | soul           | model  | tools.allow |
      | main | You are Isaac. | grover | read        |
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | model | tool_call | arguments                  |
      | echo  | read      | {"filePath": "/etc/passwd"} |
      | model | type      | content                    |
      | echo  | text      | Sorry                      |
    When the user sends "read passwords" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   | true            |

  Scenario: crew member cannot write files outside boundaries
    Given the following crew exist:
      | name | soul           | model  | tools.allow |
      | main | You are Isaac. | grover | write       |
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | model | tool_call | arguments                                          |
      | echo  | write     | {"filePath": "/tmp/evil.txt", "content": "gotcha"} |
      | model | type      | content                                            |
      | echo  | text      | Sorry                                              |
    When the user sends "write evil" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   | true            |

  Scenario: session cwd is included as an allowed directory
    Given the following crew exist:
      | name | soul           | model  | tools.allow     |
      | main | You are Isaac. | grover | read,write,edit |
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | model | tool_call | arguments                                  |
      | echo  | read      | {"filePath": "target/test-state/hello.txt"} |
      | model | type      | content                                    |
      | echo  | text      | Got it                                     |
    When the user sends "read hello" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |
