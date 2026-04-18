Feature: Bridge Commands
  The bridge intercepts input starting with / and handles it
  locally without sending it to the LLM. Unrecognized commands
  produce a helpful error.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And the following sessions exist:
      | name           | totalTokens | compactionCount |
      | bridge-status  | 5000        | 2               |
    And session "bridge-status" has transcript:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | hi              |
      | message | user         | how are you     |
      | message | assistant    | fine            |
    And the built-in tools are registered

  Scenario: /status prints session information as markdown table
    When the user sends "/status" on session "bridge-status"
    Then the output matches:
      | pattern                                      |
      | Session Status                              |
      | Crew .* main                                 |
      | ─+                                           |
      | Model .* echo \(grover\)                     |
      | Session .* bridge-status                     |
      | File .* bridge-status\.jsonl                 |
      | Turns .* 4                                   |
      | Compactions .* 2                             |
      | Context .* 5,000 / 32,768 .*15%             |
      | Soul .*                                        |
      | Tools .* \d+                                 |
      | CWD                                          |
    And the output does not contain "SOUL.md"

  Scenario: /status is not sent to the LLM
    When the user sends "/status" on session "bridge-status"
    Then session "bridge-status" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hello           |
      | message | assistant    | hi              |
      | message | user         | how are you     |
      | message | assistant    | fine            |

  Scenario: unrecognized command produces an error
    When the user sends "/bogus" on session "bridge-status"
    Then the output contains "unknown command: /bogus"

  Scenario: normal input is not intercepted by the bridge
    Given the following model responses are queued:
      | type | content   | model |
      | text | I am fine | echo  |
    When the user sends "how are you?" on session "bridge-status"
    Then session "bridge-status" has transcript matching:
      | type    | message.role | message.content |
      | message | assistant    | I am fine       |
