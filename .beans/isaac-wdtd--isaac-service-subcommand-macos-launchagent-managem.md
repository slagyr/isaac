---
# isaac-wdtd
title: 'Isaac service subcommand: macOS LaunchAgent management'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-20T18:45:14Z
updated_at: 2026-05-20T19:50:11Z
---

## Gap

There is no built-in way to run Isaac as a background service on macOS.
Operators end up hand-writing a LaunchAgent plist and remembering
launchctl incantations (`bootstrap`, `bootout`, `kickstart`,
`kickstart -k`, `print`). This is fragile (the plist drifts from Isaac's
expected boot flags) and unfriendly to anyone setting up a fresh box.

## Proposed change

Add an `isaac service` subcommand that wraps the launchctl workflow.

```
isaac service install      # write & load the LaunchAgent plist
isaac service uninstall    # bootout and remove the plist
isaac service start        # kickstart (idempotent)
isaac service stop         # kickstart -k (or bootout to keep stopped)
isaac service restart      # kickstart -k followed by kickstart
isaac service status       # is it loaded? running? last exit? PID?
isaac service logs [-f]    # tail StandardOutPath
```

## Implementation sketch

Live in `src/isaac/service/cli.clj` (following existing convention —
`isaac.server.cli`, `isaac.crew.cli`, `isaac.logs.cli`, etc.).

### Plist generation

A template plist string embedded in the namespace (~30 lines; small
enough that a separate resource file is not warranted unless operator
edit-friendliness becomes a goal). Placeholders resolved at install
time:

- `{HOME}` — `(System/getProperty "user.home")`
- `{USER}` — `(System/getenv "USER")`
- `{BB_BIN}` — `(which "bb")` (or equivalent — see open question)
- `{BB_EDN}` — `(System/getProperty "user.dir")` for the bb.edn location
- `{LOG_DIR}` — `~/Library/Logs/isaac/`

`install` writes to `~/Library/LaunchAgents/com.slagyr.isaac.plist`,
ensures `~/Library/Logs/isaac/` exists, then runs
`launchctl bootstrap gui/<uid> <path>`.

### Verbs

- **uninstall** — `launchctl bootout gui/<uid>/com.slagyr.isaac` then
  `rm -f` the plist.
- **start / stop / restart** — wrappers around
  `launchctl kickstart [-k] gui/<uid>/com.slagyr.isaac`. (For "stop and
  stay stopped" semantics, `bootout` is the right choice; `kickstart -k`
  alone restarts.)
- **status** — parses `launchctl print gui/<uid>/com.slagyr.isaac`
  for state, pid, last exit code. Prints a compact summary. Exits
  non-zero if not running.
- **logs [-f]** — `tail` (or `tail -f`) on
  `~/Library/Logs/isaac/server.log`. Convenience over remembering the
  path.

## Cross-platform

macOS-only initially. Eventually Linux uses systemd, Windows uses
Service Control Manager. Clean split:

- `isaac.service.cli` — dispatch + shared messaging.
- `isaac.service.macos`, `isaac.service.linux`, `isaac.service.windows`
  — platform implementations.

Dispatch on `(System/getProperty "os.name")`. Until Linux/Windows are
implemented, error clearly with a "not yet supported on <OS>" message.

## Test surface

### Unit specs (`spec/isaac/service/...`)

- Plist template substitution.
- `install` / `uninstall` against a faked launchctl using `with-redefs`
  on `clojure.java.shell/sh`.
- `status` parsing against a captured `launchctl print` fixture.

### Feature scenarios

Following Isaac's convention (every CLI subcommand has feature
coverage under `features/cli/`), add `features/cli/service.feature`.

> Path note: features under `features/cli/` are slated to move under
> the screaming-architecture migration (see `isaac-cqh`); when that
> ships, this file moves with the rest. Until then, the conventional
> location is correct.

Representative scenarios:

```gherkin
Feature: isaac service — macOS LaunchAgent management
  `isaac service` manages Isaac as a background service on macOS via
  launchctl. It writes a LaunchAgent plist with Isaac's invocation
  baked in, bootstraps/boots-out the agent, and exposes status and
  log access without requiring the operator to know launchctl
  incantations.

  All scenarios here assume macOS unless otherwise stated. On other
  platforms, every subcommand prints "isaac service is not yet
  supported on <OS>" and exits non-zero.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the operating system is "Mac OS X"
    And launchctl is stubbed

  Scenario: install writes the plist and bootstraps the agent
    Given "bb" resolves to "/opt/homebrew/bin/bb"
    When isaac is run with "service install"
    Then the file "~/Library/LaunchAgents/com.slagyr.isaac.plist" exists
    And the plist contains:
      | path                | value                           |
      | Label               | com.slagyr.isaac                |
      | ProgramArguments[0] | /opt/homebrew/bin/bb            |
      | StandardOutPath     | ~/Library/Logs/isaac/server.log |
    And launchctl was called with "bootstrap gui/<uid> ~/Library/LaunchAgents/com.slagyr.isaac.plist"
    And the stdout contains "Resolved bb: /opt/homebrew/bin/bb"
    And the exit code is 0

  Scenario: install errors clearly when bb is not on PATH
    Given "bb" is not on PATH
    When isaac is run with "service install"
    Then the stderr contains "could not locate bb"
    And the stderr contains "pass --bb-bin <path>"
    And the file "~/Library/LaunchAgents/com.slagyr.isaac.plist" does not exist
    And the exit code is 1

  Scenario: install accepts --bb-bin override
    Given "bb" is not on PATH
    When isaac is run with "service install --bb-bin /usr/local/bin/bb"
    Then the plist contains:
      | path                | value             |
      | ProgramArguments[0] | /usr/local/bin/bb |
    And the exit code is 0

  Scenario: status shows running with pid and last exit
    Given the service is installed
    And launchctl print returns:
      """
      state = running
      pid = 51234
      last exit code = 0
      """
    When isaac is run with "service status"
    Then the stdout matches:
      | pattern         |
      | state: running  |
      | pid:   51234    |
      | last exit: 0    |
    And the exit code is 0

  Scenario: status on a not-installed service is unambiguous
    Given the service is not installed
    When isaac is run with "service status"
    Then the stdout contains "not installed"
    And the exit code is 1

  Scenario: uninstall is idempotent when the service is absent
    Given the service is not installed
    When isaac is run with "service uninstall"
    Then the stdout contains "already uninstalled"
    And the exit code is 0

  Scenario: restart kicks the agent
    Given the service is installed and running
    When isaac is run with "service restart"
    Then launchctl was called with "kickstart -k gui/<uid>/com.slagyr.isaac"
    And the exit code is 0

  Scenario: logs prints recent entries
    Given the service is installed
    And the file "~/Library/Logs/isaac/server.log" contains:
      """
      11:15:15.692  INFO   :server/started  {:port 6674}
      """
    When isaac is run with "service logs"
    Then the stdout contains ":server/started"
    And the exit code is 0

  Scenario: Linux is not yet supported
    Given the operating system is "Linux"
    When isaac is run with "service install"
    Then the stderr contains "not yet supported on Linux"
    And the exit code is 1
```

### New step plumbing

Three small step-def additions (helpers live in
`spec/isaac/service/features/helpers/service.clj`,
phrases in
`spec/isaac/service/features/steps/service.clj`):

1. `Given launchctl is stubbed` + `Given launchctl print returns: <docstring>`
   — installs a `with-redefs` on `clojure.java.shell/sh` that captures
   launchctl invocations and returns canned output. Same shape as the
   existing HTTP stub in `server_steps`.
2. `Then launchctl was called with "..."` — asserts against captured
   invocations.
3. `Given the operating system is "..."` — `with-redefs` on
   `(System/getProperty "os.name")`.

Everything else (`isaac is run with`, `the file ... exists`,
`the stdout contains/matches`, `the exit code is N`) already exists.

### Judgment calls in the scenario shape

- **Tilde paths in assertions.** Scenarios read more naturally with
  `~/Library/...`. The step that asserts file existence should expand
  `~` before checking.
- **Plist content assertion.** The `| path | value |` table assumes a
  parsed-plist assertion (i.e., load the XML, then index into the
  resulting map by key path like `ProgramArguments[0]`). Cleaner than
  raw-substring matching against XML.

## Operator concern — macOS Automation grants

macOS Automation / Full Disk Access grants are keyed to the **binary
path**. If `which bb` ever changes (Intel `/usr/local/bin/bb` vs Apple
Silicon `/opt/homebrew/bin/bb`, or a future Babashka install moves
paths), prior grants don't carry forward. The user has to re-grant
Automation/FDA to the new path before the service can send iMessages
or access protected directories.

`install` should **print the resolved `BB_BIN` path** so the operator
can verify the grant matches. Optionally add a `service doctor`
subcommand later that flags suspected grant drift.

## Open questions

1. **`which bb` failure mode.** If bb is not on PATH at install time,
   abort with a clear error, or accept a `--bb-bin <path>` override?
   Likely both — error by default, override for non-standard setups.
2. **Resolution of `BB_EDN`.** Sketch uses `(System/getProperty
   "user.dir")`, which assumes install is invoked from the Isaac repo
   root. Should `install` accept `--isaac-dir <path>` for invocations
   from elsewhere?
3. **Plist label.** Sketch uses `com.slagyr.isaac`. Confirm that's the
   right reverse-DNS identifier. Multiple Isaac installs on one host
   would need distinct labels (e.g. `com.slagyr.isaac.<instance>`).
4. **`stop` semantics.** Two reasonable interpretations:
   `kickstart -k` (restarts according to KeepAlive policy) vs `bootout`
   (unload until next `start`). Picking one or exposing both
   (`stop`/`disable`)?
5. **Where the server boots from.** The plist needs an argv that ends
   up running `bb isaac server` (or equivalent). Confirm the exact
   invocation, including any default flags.

## Origin

Requested by Micah while working through running Isaac as a managed
service rather than a foreground shell process.
