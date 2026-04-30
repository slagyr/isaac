Feature: Sessions Command
  `isaac sessions` lists stored conversation sessions.

  Background:
    Given default Grover setup
    And the isaac EDN file "config/crew/ketch.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are a pirate. |

  Scenario: sessions is registered and has help
    When isaac is run with "help sessions"
    Then the stdout contains "Usage: isaac sessions"
    And the exit code is 0

  Scenario: sessions lists all sessions
    Given the following sessions exist:
      | name         | total-tokens | updated-at           |
      | design-chat  | 5000        | 2026-04-12T15:00:00 |
      | review-chat  | 778         | 2026-04-12T10:00:00 |
      | pirate-chat  | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions"
    Then the stdout matches:
      | pattern      |
      | design-chat  |
      | review-chat  |
      | pirate-chat  |
    And the exit code is 0

  Scenario: sessions --crew filters by current crew member
    Given the following sessions exist:
      | name         | crew  | total-tokens | updated-at           |
      | design-chat  | main  | 5000        | 2026-04-12T15:00:00 |
      | pirate-chat  | ketch | 12000       | 2026-04-11T10:00:00 |
    When isaac is run with "sessions --crew ketch"
    Then the stdout matches:
      | pattern      |
      | pirate-chat  |
    And the stdout does not contain "design-chat"
    And the exit code is 0

  Scenario: sessions with no sessions prints a message
    When isaac is run with "sessions"
    Then the stdout contains "no sessions"
    And the exit code is 0

  Scenario: sessions --crew with unknown crew member prints an error
    When isaac is run with "sessions --crew nonexistent"
    Then the stderr contains "unknown crew"
    And the stderr contains "nonexistent"
    And the exit code is 1

  Scenario: sessions output has aligned columns with a header row
    Given the following sessions exist:
      | name         | total-tokens | updated-at           |
      | design-chat  | 5000         | 2026-04-12T15:00:00  |
      | review-chat  | 778          | 2026-04-12T10:00:00  |
      | pirate-chat  | 12000        | 2026-04-11T10:00:00  |
    When isaac is run with "sessions --crew main"
    Then the stdout matches:
      | pattern                                       |
      | SESSION\s+AGE\s+USED\s+WINDOW\s+PCT           |
      | design-chat\s+\S+\s+5,000\s+32,768\s+\d+%     |
      | review-chat\s+\S+\s+778\s+32,768\s+\d+%       |
      | pirate-chat\s+\S+\s+12,000\s+32,768\s+\d+%    |

  Scenario: sessions show prints metadata for one session
    Given the following sessions exist:
      | name        | total-tokens | updated-at          |
      | design-chat | 5000         | 2026-04-12T15:00:00 |
    And session "design-chat" has transcript:
      | type    | message.role | message.content     |
      | message | user         | Hello there         |
      | message | assistant    | Hi, how can I help? |
    When isaac is run with "sessions show design-chat"
    Then the exit code is 0
    And the stdout matches:
      | pattern                   |
      | Session Status            |
      | Crew .* main              |
      | Model .* echo \(grover\)  |
      | Session .* design-chat    |
      | Turns .* 2                |
      | Context .* 5,000 / 32,768 |
    And the stdout does not contain "Hello there"

  Scenario: sessions delete removes a session and its transcript
    Given the following sessions exist:
      | name        |
      | design-chat |
    And session "design-chat" has transcript:
      | type    | message.role | message.content |
      | message | user         | hi              |
    When isaac is run with "sessions delete design-chat"
    Then the exit code is 0
    And session "design-chat" does not exist
    And the isaac file "sessions/design-chat.jsonl" does not exist
