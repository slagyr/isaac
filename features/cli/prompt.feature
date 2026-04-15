Feature: Prompt single-turn command
  `isaac prompt` runs a single turn and exits. Conversations
  persist across invocations via --session.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: prompt command runs one turn and exits
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When isaac is run with "prompt -m 'What is 2+2?'"
    Then the output contains "Four, I think"
    And the exit code is 0

  Scenario: Default session is prompt-default
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "prompt -m 'Hi'"
    Then the following sessions match:
      | id              |
      | prompt-default  |
    And session "prompt-default" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | Hi              |
      | message | assistant    | Hello           |

  Scenario: --session resumes an existing session
    Given the following sessions exist:
      | name           |
      | prompt-resume  |
    And session "prompt-resume" has transcript:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
    And the following model responses are queued:
      | type | content | model |
      | text | New one | echo  |
    When isaac is run with "prompt -m 'Next' --session prompt-resume"
    Then session "prompt-resume" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
      | message | user         | Next            |
      | message | assistant    | New one         |

  Scenario: Missing --message exits non-zero
    When isaac is run with "prompt"
    Then the output contains "required"
    And the exit code is 1

  Scenario: --json outputs structured result
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "prompt -m 'Hi' --json"
    Then the output contains "response"
    And the output contains "Hello"
    And the exit code is 0

  Scenario: Provider error exits non-zero
    Given the following model responses are queued:
      | type  | content                 | model |
      | error | context length exceeded | echo  |
    When isaac is run with "prompt -m 'Hi'"
    Then the exit code is 1

  Scenario: --crew resolves the crew member's model
    Given the following models exist:
      | alias   | model    | provider | contextWindow |
      | grover  | echo     | grover   | 32768         |
      | grover2 | echo-alt | grover   | 16384         |
    And the following crew exist:
      | name  | soul              | model   |
      | main  | You are Isaac.    | grover  |
      | ketch | You are a pirate. | grover2 |
    And the following model responses are queued:
      | model    | type | content |
      | echo-alt | text | Ahoy    |
    When isaac is run with "prompt --crew ketch -m 'hello'"
    Then the exit code is 0
    And session "prompt-default" has transcript matching:
      | type    | message.model | message.crew |
      | message | echo-alt      | ketch        |

  Scenario: --crew uses the crew member's soul
    Given the following crew exist:
      | name  | soul              | model  |
      | main  | You are Isaac.    | grover |
      | ketch | You are a pirate. | grover |
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | Arr     |
    When isaac is run with "prompt --crew ketch -m 'hello'"
    Then the exit code is 0
    And the following sessions match:
      | id             | crew  |
      | prompt-default | ketch |
