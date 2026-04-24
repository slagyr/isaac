Feature: Prompt single-turn command
  `isaac prompt` runs a single turn and exits. Conversations
  persist across invocations via --session.

  Background:
    Given default Grover setup

  Scenario: prompt command runs one turn and exits
    Given the following model responses are queued:
      | type | content       | model |
      | text | Four, I think | echo  |
    When isaac is run with "prompt -m 'What is 2+2?'"
    Then the stdout contains "Four, I think"
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
    Then the stdout contains "required"
    And the exit code is 1

  Scenario: --json outputs structured result
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "prompt -m 'Hi' --json"
    Then the stdout contains "response"
    And the stdout contains "Hello"
    And the exit code is 0

  Scenario: Provider error prints a readable message to stderr
    Given the following model responses are queued:
      | model | type  | content                 |
      | echo  | error | context length exceeded |
    When isaac is run with "prompt -m 'Hi'"
    Then the stderr contains "context length exceeded"
    And the exit code is 1

  Scenario: --crew resolves the crew member's model
    Given the isaac EDN file "config/models/grover.edn" exists with:
      | path | value |
      | model | echo |
      | provider | grover |
      | context-window | 32768 |
    And the isaac EDN file "config/models/grover2.edn" exists with:
      | path | value |
      | model | echo-alt |
      | provider | grover |
      | context-window | 16384 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are Isaac. |
    And the isaac EDN file "config/crew/ketch.edn" exists with:
      | path | value |
      | model | grover2 |
      | soul | You are a pirate. |
    And the following model responses are queued:
      | model    | type | content |
      | echo-alt | text | Ahoy    |
    When isaac is run with "prompt --crew ketch -m 'hello'"
    Then the exit code is 0
    And session "prompt-default" has transcript matching:
      | type    | message.model | message.crew |
      | message | echo-alt      | ketch        |

  Scenario: prompt sets cwd on the created session
    Given the following model responses are queued:
      | type | content | model |
      | text | Hi      | echo  |
    When isaac is run with "prompt -m 'Hi'"
    Then the following sessions match:
      | id             | cwd |
      | prompt-default | #*  |

  Scenario: --crew uses the crew member's soul
    Given the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are Isaac. |
    And the isaac EDN file "config/crew/ketch.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are a pirate. |
    And the following model responses are queued:
      | model | type | content |
      | echo  | text | Arr     |
    When isaac is run with "prompt --crew ketch -m 'hello'"
    Then the exit code is 0
    And the following sessions match:
      | id             | crew  |
      | prompt-default | ketch |

  Scenario: prompt-created sessions load AGENTS.md from cwd
    Given the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "prompt -m 'Hi'"
    Then the exit code is 0
    And the system prompt contains "Micah's AI assistant management tools."

  Scenario: --resume uses the most recent session
    Given the following sessions exist:
      | name    | updatedAt           |
      | older   | 2026-04-10T10:00:00 |
      | recent  | 2026-04-12T15:00:00 |
    And session "recent" has transcript:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
    And the following model responses are queued:
      | type | content   | model |
      | text | Continued | echo  |
    When isaac is run with "prompt --resume -m 'Next'"
    Then session "recent" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | Earlier         |
      | message | assistant    | Earlier reply   |
      | message | user         | Next            |
      | message | assistant    | Continued       |

  Scenario: --resume with no existing sessions creates one
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    When isaac is run with "prompt --resume -m 'Hi'"
    Then the exit code is 0
    And the stdout contains "Hello"
