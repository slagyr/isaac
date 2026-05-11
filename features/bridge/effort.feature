@wip
Feature: /effort Command
  The /effort bridge command sets the session-level effort override
  (integer 0-10). Subsequent turns resolve effort with the session
  value at the top of the chain. The setting persists on the session
  record.

  Background:
    Given default Grover setup

  Scenario: /effort updates the session's effort
    Given the following sessions exist:
      | name        |
      | effort-test |
    When the user sends "/effort 9" on session "effort-test"
    Then the reply contains "effort set to 9"
    And the following sessions match:
      | id          | effort |
      | effort-test | 9      |

  Scenario: /effort with no argument shows the current effective effort
    Given the following sessions exist:
      | name        | effort |
      | effort-test | 6      |
    When the user sends "/effort" on session "effort-test"
    Then the reply contains "effort is 6"

  Scenario: /effort clear removes the session-level override
    Given the isaac EDN file "config/crew/main.edn" exists with:
      | path   | value |
      | effort | 5     |
    And the following sessions exist:
      | name        | effort |
      | effort-test | 9      |
    When the user sends "/effort clear" on session "effort-test"
    Then the reply contains "effort cleared"
    And the following sessions match:
      | id          | effort |
      | effort-test |        |

  Scenario Outline: /effort with an invalid argument is rejected
    Given the following sessions exist:
      | name        |
      | effort-test |
    When the user sends "/effort <input>" on session "effort-test"
    Then the reply contains "effort must be an integer between 0 and 10"
    And the following sessions match:
      | id          | effort |
      | effort-test |        |

    Examples:
      | input |
      | 11    |
      | -1    |
      | foo   |
