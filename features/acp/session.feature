Feature: ACP Session Lifecycle
  ACP sessions map to Isaac's persistent session storage so users can
  resume conversations across TUI restarts.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And the ACP client has initialized

  Scenario: session/new creates an Isaac session under the acp channel
    When the ACP client sends request 2:
      | key        | value        |
      | method     | session/new  |
      | params.cwd | /tmp/project |
    Then the ACP agent sends response 2:
      | key              | value                       |
      | result.sessionId | #"agent:main:acp:direct:.+" |
    And agent "main" has sessions matching:
      | key                         |
      | #"agent:main:acp:direct:.+" |

  @wip
  Scenario: session/load resumes a prior session
    Given agent "main" has sessions:
      | key                                 |
      | agent:main:acp:direct:prior-session |
    And session "agent:main:acp:direct:prior-session" has transcript:
      | type    | message.role | message.content |
      | message | user         | What's up?      |
      | message | assistant    | All good        |
    When the ACP client sends request 3:
      | key              | value                               |
      | method           | session/load                        |
      | params.sessionId | agent:main:acp:direct:prior-session |
      | params.cwd       | /tmp/project                        |
    Then the ACP agent sends response 3:
      | key              | value                               |
      | result.sessionId | agent:main:acp:direct:prior-session |
