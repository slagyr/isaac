Feature: /crew Command
  The /crew bridge command switches the session's active crew
  member. Subsequent turns use the new crew member's soul, model,
  and provider. The change is stored in the session.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the isaac EDN file "config/models/grover.edn" exists with:
      | path | value |
      | model | echo |
      | provider | grover |
      | context-window | 32768 |
    And the isaac EDN file "config/crew/main.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are Isaac. |
    And the isaac EDN file "config/crew/ketch.edn" exists with:
      | path | value |
      | model | grover |
      | soul | You are a pirate. |

  Scenario: /crew switches the session's active crew member
    Given the following sessions exist:
      | name      |
      | crew-test |
    When the user sends "/crew ketch" on session "crew-test"
    Then the reply contains "switched crew to ketch"
    And the following sessions match:
      | id        | crew  |
      | crew-test | ketch |

  Scenario: /crew persists across turns
    Given the following sessions exist:
      | name      |
      | crew-test |
    And the following model responses are queued:
      | type | content | model |
      | text | Ahoy    | echo  |
      | text | Arr     | echo  |
    When the user sends "/crew ketch" on session "crew-test"
    And the user sends "hi" on session "crew-test"
    And the user sends "bye" on session "crew-test"
    Then session "crew-test" has transcript matching:
      | type    | message.role | message.crew |
      | message | assistant    | ketch        |
      | message | assistant    | ketch        |

  Scenario: /crew with no argument shows the current crew member
    Given the following sessions exist:
      | name      |
      | crew-test |
    When the user sends "/crew" on session "crew-test"
    Then the reply contains "main is the current crew member"

  Scenario: /crew with unknown name shows an error
    Given the following sessions exist:
      | name      |
      | crew-test |
    When the user sends "/crew nonexistent" on session "crew-test"
    Then the reply contains "unknown crew: nonexistent"
