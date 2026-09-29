---
# isaac-1i1x
title: 'isaac-hail CLI: hail send crashes on a real host — no session store in the CLI process'
status: completed
type: bug
priority: critical
created_at: 2026-09-29T18:42:29Z
updated_at: 2026-09-29T19:14:34Z
---

Found 2026-09-29 smoking the zanebot deploy (hail e95274a, agent 6aa86a3; rolled back). `isaac hail send --band smoke --prompt …` from a shell on zanebot → `IllegalArgumentException: No implementation of method: :list-sessions of protocol: isaac.session.policy/SessionPolicy found for: nil`. Since isaac-ex4q, send resolves sessions at send time (Agent submit / frequencies resolver) and the CLI process has no registered session store. The feature suite runs the CLI in-process with a store registered, so it never saw this. `POST /hail/send` (in the server) works — 201 with a turn id. Crews and skills on zanebot call `isaac hail send` from the shell.

Fix direction: the CLI send must either run inside the server (proxy the send to the server like other server-backed commands) or bootstrap the session store it needs. A feature must exercise the packaged CLI path (separate process), not `main/run` in-process.


## Acceptance (2026-09-29, Micah approved the new step)

- [ ] `bb features features/handoff.feature:116` (isaac-hail, `@wip` on main at fcb5d60) passes with `@wip` removed.
- [ ] New step in isaac-hail feature-steps: `the next isaac command starts in a fresh process` — clears the process-wide registrations the harness sets up (the registered session store; the CLI host's installed-runtime memo) so the next `isaac is run with` sees what a real shell sees.
- [ ] Fix: `hail send` (CLI) calls `host/ensure-runtime!` with Agent's `runtime/install!` (as `isaac sessions`, `prompt`, `turns` do) before submitting. Check the HTTP route and the `hail-send` tool still work (they run in the server).
- [ ] Whole Hail suite green; version bump; `bb ci`.

feature-baseline: isaac-hail fcb5d60f751383c45b8b5b6c8fb5ed67c73fd97a
feature-blob: isaac-hail features/handoff.feature 7d28c779d2196b20647afada953c4837fe346696 116

## Landed on main (2026-09-29)

main-sha: isaac-hail 8a8ad252bbc8cc9fda58b11fabd8cb59f8999cc7

Confirmed the fresh-process step reproduces the exact zanebot error
(`IllegalArgumentException: No implementation of method: :list-sessions … found
for: nil`) before the fix, and passes after it. `hail send` now calls
`host/ensure-runtime!` with Agent's `runtime/install!` (loaded config) before
resolving/submitting, mirroring `isaac.session.cli/install-cli!`. HTTP route and
hail-send tool unaffected (they call `isaac.hail.queue/send!` directly inside a
live server runtime). `bb ci` green: 64 specs, 104 features. Version 0.1.25 ->
0.1.26.
