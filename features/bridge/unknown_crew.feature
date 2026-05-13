Feature: Unknown crew rejects the turn
  A session whose :crew references a name not defined in config
  cannot take a turn: the crew owns the session's memory, soul,
  and tools. Proceeding would silently use the defaults.

  The bridge rejects the turn with a clear message pointing at
  /crew, logs a rejected event, and leaves the session intact.
  The user either re-adds the crew to config or switches the
  session to a known crew; subsequent turns are evaluated
  independently (each one is accepted or rejected on its own).

  Background:
    Given default Grover setup
    And the following sessions exist:
      | name  | crew   |
      | stale | marvin |

  Scenario: a turn on a session whose crew is unknown is rejected with guidance
    When the user sends "hello" on session "stale"
    Then the reply contains "unknown crew: marvin"
    And the reply contains "use /crew {name} to switch, or add marvin to config"
    And the log has entries matching:
      | level | event          | session | crew   | reason        |
      | :warn | :drive/turn-rejected | stale   | marvin | :unknown-crew |

  Scenario: switching the rejected session to a known crew restores normal turns
    When the user sends "hello" on session "stale"
    Then the reply contains "unknown crew: marvin"
    And the log has entries matching:
      | level | event          | session | crew   | reason        |
      | :warn | :drive/turn-rejected | stale   | marvin | :unknown-crew |

    When the user sends "/crew main" on session "stale"
    Then the reply contains "switched crew to main"
    And the log has entries matching:
      | level | event                 | session | from   | to   |
      | :info | :session/crew-changed | stale   | marvin | main |

    When the user sends "try again" on session "stale"
    Then the system prompt contains "You are Isaac."
    And the log has entries matching:
      | level | event          | session | crew |
      | :info | :drive/turn-accepted | stale   | main |
