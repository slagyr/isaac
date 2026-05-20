---
# isaac-wdtd
title: 'Isaac service subcommand: macOS LaunchAgent management'
status: todo
type: feature
priority: normal
created_at: 2026-05-20T18:45:14Z
updated_at: 2026-05-20T18:45:14Z
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

- Unit specs for plist template substitution.
- `install` / `uninstall` against a faked launchctl using `with-redefs`
  on `clojure.java.shell/sh`.
- `status` parsing against a captured `launchctl print` fixture.
- No feature-level scenario — this is a CLI affordance, not an app
  behavior contract.

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
