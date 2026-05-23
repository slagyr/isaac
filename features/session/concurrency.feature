@wip
Feature: Crew and session concurrency
  Sessions track a transient `in-flight?` state, set when a turn
  starts and cleared when it ends. The session store enforces the
  session-level invariant — at most one turn runs on a session at
  a time — by queueing second-and-later dispatches behind the
  in-flight turn. Callers consult `can-dispatch?` to decide
  whether to route work elsewhere (crew capacity), but `dispatch!`
  itself never refuses on session collision.

  Background:
    Given default Grover setup

  Scenario: a real turn marks its session in-flight, then clears it
    Given the following sessions exist:
      | name |
      | s1   |
    And the following model responses are queued:
      | type | content | model | wait |
      | text | ok      | echo  | true |
    When the user sends "hi" on session "s1"
    Then session "s1" in-flight status is true
    When the turn ends on session "s1"
    Then session "s1" in-flight status is false

  Scenario: a second turn on the same session queues behind the first
    Given the following sessions exist:
      | name |
      | s1   |
    And the following model responses are queued:
      | type | content | model | wait |
      | text | first   | echo  | true |
      | text | second  | echo  |      |
    When the user sends "hi" on session "s1"
    And the user sends "go again" on session "s1"
    Then session "s1" queue depth is 1
    When the turn ends on session "s1"
    Then session "s1" queue depth is 0
    And session "s1" has transcript matching:
      | type    | message.role | message.content |
      | message | user         | hi              |
      | message | assistant    | first           |
      | message | user         | go again        |
      | message | assistant    | second          |

  Scenario: in-flight clears when a turn errors
    Given the following sessions exist:
      | name |
      | s1   |
    And the following model responses are queued:
      | type  | content | model |
      | error | boom    | echo  |
    When the user sends "hi" on session "s1"
    Then session "s1" in-flight status is false
