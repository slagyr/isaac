Feature: Scheduler per-task policies
  Each task can specify :coalesce, :on-error, and :timeout-ms. These
  govern how the scheduler reacts when fires overlap, handlers throw,
  or handlers hang.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the scheduler is started with the clock at "2026-05-20T10:00:00Z"

  Scenario: coalesce :skip drops overlapping fires
    Given a scheduled task:
      | id   | trigger.kind | trigger.ms | handler-runtime | coalesce |
      | slow | interval     | 100        | 250ms           | skip     |
    When the clock advances "300ms" and pending handlers complete
    Then handler "slow" started 1 time

  Scenario: coalesce :queue runs overlapping fires sequentially
    Given a scheduled task:
      | id   | trigger.kind | trigger.ms | handler-runtime | coalesce |
      | slow | interval     | 100        | 250ms           | queue    |
    When the clock advances "300ms" and pending handlers complete
    Then handler "slow" started 3 times

  Scenario: on-error :log (default) logs and keeps scheduling
    Given a scheduled task:
      | id    | trigger.kind | trigger.ms | handler-throws |
      | flaky | interval     | 100        | true           |
    When the clock advances "300ms"
    Then handler "flaky" has fired 3 times
    And the log has entries matching:
      | level | event                      | id    |
      | error | :scheduler/handler-error   | flaky |
      | error | :scheduler/handler-error   | flaky |
      | error | :scheduler/handler-error   | flaky |

  Scenario: on-error :retry-with-backoff delays the next fire after a throw
    Given a scheduled task:
      | id    | trigger.kind | trigger.ms | handler-throws | on-error           | backoff-ms |
      | retry | interval     | 100        | true           | retry-with-backoff | 500        |
    When the clock advances "100ms"
    Then handler "retry" has fired 1 time
    # Normally would fire again at 200ms, but backoff delays it to 600ms.
    When the clock advances "499ms"
    Then handler "retry" has fired 1 time
    When the clock advances "1ms"
    Then handler "retry" has fired 2 times

  Scenario: on-error :disable-after-N stops the task after N consecutive throws
    Given a scheduled task:
      | id    | trigger.kind | trigger.ms | handler-throws | on-error        | disable-after |
      | flaky | interval     | 100        | true           | disable-after-N | 3             |
    When the clock advances "1s"
    Then handler "flaky" has fired 3 times
    And the log has entries matching:
      | level | event                | id    | reason            |
      | warn  | :scheduler/disabled  | flaky | :too-many-errors  |
    And the scheduled tasks do not include "flaky"

  Scenario: timeout-ms interrupts hung handlers
    Given a scheduled task:
      | id   | trigger.kind | trigger.ms | handler-runtime | timeout-ms |
      | hang | interval     | 100        | 5s              | 200ms      |
    When the clock advances "300ms" and pending handlers complete
    Then the log has entries matching:
      | level | event              | id   |
      | warn  | :scheduler/timeout | hang |
    And handler "hang" started 1 time
