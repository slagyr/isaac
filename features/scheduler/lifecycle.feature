Feature: Scheduler lifecycle and isolation
  The scheduler starts and stops with isaac.system. A slow or hung
  handler must not delay the timer or other tasks — this is the
  critical property of the two-layer (timer + virtual-thread work
  executor) design.

  Background:
    Given an in-memory Isaac state directory "target/test-state"

  Scenario: stopping the scheduler cancels every registered task
    Given the scheduler is started with the clock at "2026-05-20T10:00:00Z"
    And a scheduled task:
      | id   | trigger.kind | trigger.ms |
      | tick | interval     | 100        |
    When the scheduler stops
    And the clock advances "300ms"
    Then handler "tick" has not fired

  Scenario: a hung handler does not delay other tasks
    Given the scheduler is started with the clock at "2026-05-20T10:00:00Z"
    And a scheduled task:
      | id   | trigger.kind | trigger.ms | handler-runtime |
      | slow | interval     | 100        | 5s              |
    And a scheduled task:
      | id   | trigger.kind | trigger.ms | handler-runtime |
      | fast | interval     | 100        | 1ms             |
    When the clock advances "300ms" and pending handlers complete
    Then handler "fast" started 3 times

  Scenario: scheduler is started and stopped with isaac.system
    When the Isaac system is started
    Then the scheduler is running
    When the Isaac system is stopped
    Then the scheduler is not running
