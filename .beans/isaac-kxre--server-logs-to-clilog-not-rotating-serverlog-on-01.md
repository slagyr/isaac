---
# isaac-kxre
title: Server logs to cli.log, not rotating server.log, on 0.1.15 (stale isaac-server pin); rename 'sink' logging vocabulary
status: in-progress
type: bug
priority: high
tags:
    - logging
    - deploy
    - unverified
created_at: 2026-06-30T18:01:18Z
updated_at: 2026-06-30T18:09:57Z
---

## Symptom (live 0.1.15 deploy)

The running server writes its **structured log entries into `~/.isaac/logs/cli.log`**;
`~/.isaac/logs/server.log` is never created, and `cli.log` does not rotate (already 1.6 MB+).

Proof — `cli.log` tail holds pure server-origin entries:

```
{:event :server/boot-summary, :modules 9, :loaded 6, :activated 9 ...}
{:event :server/started, :port 6674, :host "127.0.0.1", :file "isaac/server/cli.clj" ...}
```

(The server's *console* stdout/stderr — the hello banner, Clojure warnings — go to
`~/Library/Logs/isaac/server.log` via the launchd plist's `StandardOutPath`; that is
separate from the structured logger and is not the rotating server log.)

## Root cause: stale module pin (the code fix already exists upstream)

This is **not a fresh code defect** — it is a release-coordination gap:

- The `gfsq` fix (the `configure-server-sink!` caller that binds the rotating
  `<root>/logs/server.log`) is on **isaac-server main `83706a06`**
  ("Close server.log sink verification gap (isaac-gfsq)").
- **foundation 0.1.15 pins isaac-server `468c2610`**, which is **19 commits behind main**
  (`compare 468c2610...main` → ahead_by=19, behind_by=0). `468c2610` predates the fix.
- On `468c2610`, `server/cli.clj run` only touches logging inside `(when logs ...)`
  (the `--logs` tail path). The launchd plist runs `... server` **without** `--logs`,
  so nothing wires the server sink.
- foundation 0.1.15 *does* carry the helper `log/output.clj apply-server!`, but it has
  **zero call sites** in the deployed bundle (`apply-server!` is dead code there) because
  its caller lives in isaac-server at a SHA newer than 0.1.15 pinned.
- Net in `logger/save-entry`: `:output` is `:file` (the default), `server-sink?` is
  `false` → falls through to `:log-file`, which `main.clj configure-cli-logging!` →
  `apply-cli!` bound to `cli.log`. So server entries land in `cli.log`.

This also **inverts the x2po origin-separation goal**: instead of keeping CLI entries out
of the server log, server entries are being dumped into the CLI log.

## Resolution

1. **Ship the fix.** Bump the `isaac-server` module pin to `>= 83706a06` (current main) in
   the module registry / foundation manifest, re-cut per RELEASE.md, and redeploy.
   Then verify on the box: `~/.isaac/logs/server.log` is created and rotates, and
   `cli.log` no longer receives server-origin entries.
2. **Rename the "sink" vocabulary** — it reads wrong for logs. Behavior-preserving refactor
   across foundation (`logger.clj`, `log/file.clj`, `log/output.clj`) and the isaac-server
   caller + specs:
   - `sink-state` → ?
   - `server-sink?` → ?
   - `configure-server-sink!` → ?
   - `configure-cli-sink!` → ?
   - `clear-sink-config!` → ?

   **Proposed term: `writer`** (a log *writer* — the destination that appends entries to a
   file): `writer-state`, `server-writer?`, `configure-server-writer!`,
   `configure-cli-writer!`, `clear-writer-config!`. **Term pending Micah's confirmation**
   before implementing — alternatives considered: "appender", "log-file".

## Acceptance (gherkin — for the worker to flesh into steps)

```gherkin
Scenario: a booted server writes structured entries to the server log, not the CLI log
  Given an isaac server is started without --logs
  When the server logs a structured entry
  Then "logs/server.log" contains that entry
  And "logs/cli.log" does not contain server-origin entries

Scenario: the server log rotates at the configured threshold
  Given an isaac server writing to "logs/server.log"
  When the server log exceeds the rotation threshold
  Then a rotated archive "logs/server-YYYYMMDD.log" exists
  And "logs/server.log" continues from a fresh file
```

(The `sink → writer` rename is a refactor with no behavioral change — no gherkin needed for it.)

## Related

- isaac-gfsq — the upstream fix (completed) that this ships.
- isaac-x2po — log origin separation (goal inverted by this bug).
- isaac-f0fq — `:logging :output` config berth.
- The foundation 0.1.15 release (which exposed the stale pin).
