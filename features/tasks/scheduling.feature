Feature: Scheduled tasks
  Isaac fires scheduled user prompts at cron-expression intervals.
  Each task specifies: name, cron schedule, crew, and the prompt
  to send. When a task fires, it acts as if a user sent that prompt
  on behalf of the configured crew. The run's result is written back
  to the task file as last-run and last-status.

  Standard 5-field cron (minute hour day month weekday) interpreted
  in the timezone from root config :tz (falling back to the JVM
  system default). Missed windows (while Isaac was down) are skipped
  silently with a warn log.

  One-off tasks (:at instead of :cron) and task-to-comm delivery are
  deferred to separate beads.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | context-window |
      | grover | echo  | grover   | 32768          |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  @wip
  Scenario: a task fires at its cron schedule and runs a turn with its prompt
    Given config:
      | tz                       | America/Chicago |
      | sessions.naming-strategy | sequential      |
    And the following tasks exist:
      | name         | cron      | crew | input                   |
      | health-check | 0 9 * * * | main | Run the health checkin. |
    And the following model responses are queued:
      | type | content         | model |
      | text | Health is good. | echo  |
    When the scheduler ticks at "2026-04-21T09:00:00-0500"
    Then session "session-1" has transcript matching:
      | type    | message.role | message.content         |
      | message | user         | Run the health checkin. |
      | message | assistant    | Health is good.         |

  @wip
  Scenario: a cron window missed while Isaac was down is skipped silently
    Given config:
      | tz                       | America/Chicago |
      | sessions.naming-strategy | sequential      |
    And the following tasks exist:
      | name         | cron      | crew | input                   |
      | health-check | 0 9 * * * | main | Run the health checkin. |
    When the scheduler ticks at "2026-04-21T11:30:00-0500"
    Then session "session-1" does not exist
    And the log has entries matching:
      | level | event                  | task         |
      | warn  | :tasks/missed-schedule | health-check |

  @wip
  Scenario: successful task runs update the task file with last-run and last-status
    Given config:
      | tz                       | America/Chicago |
      | sessions.naming-strategy | sequential      |
    And the following tasks exist:
      | name         | cron      | crew | input                   |
      | health-check | 0 9 * * * | main | Run the health checkin. |
    And the following model responses are queued:
      | type | content         | model |
      | text | Health is good. | echo  |
    When the scheduler ticks at "2026-04-21T09:00:00-0500"
    Then the task "health-check" has:
      | last-run    | 2026-04-21T09:00:00-0500 |
      | last-status | succeeded                |
