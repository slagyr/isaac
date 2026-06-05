@wip
Feature: Foundation works without isaac-server installed
  After the phase-9 split, the `isaac` foundation ships only the
  CLI dispatcher, module loader, schema runtime, system/nexus, fs,
  logger, scheduler primitives, root resolution, and Module
  protocol. Platform features — sessions, chat, comms, tools, etc.
  — move into the `isaac-server` module and are only available when
  the user installs it.

  This feature exists to prove the split actually happened. Every
  *other* existing test (chat, sessions, comm, hail, etc.)
  implicitly proves the inverse — that installing isaac-server
  brings the platform back — so we don't need a companion scenario
  for that.

  Scenario: Foundation alone has no platform commands
    Given an empty Isaac state directory "/tmp/marigold"
    And the isaac file "isaac.edn" exists with:
      """
      {:modules {}}
      """
    When isaac is run with "help"
    Then the stdout contains "Usage: isaac"
    And the stdout does not contain "Usage: isaac chat"
    And the stdout does not contain "Usage: isaac sessions"
    And the exit code is 0
