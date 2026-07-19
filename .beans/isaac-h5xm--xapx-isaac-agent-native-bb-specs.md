---
# isaac-h5xm
title: 'xapx: isaac-agent — native bb specs'
status: completed
type: task
priority: high
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T19:05:33Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-agent** `bb spec` / `bb features` / `bb ci` to run natively in babashka via the shared `bb.test-tasks` runner — no `clojure -M` shell-out, streamed output.

## Current
`bb.edn`: `(apply sh/sh "clojure" "-M:spec" ...)`, `-M:features`, and a `ci` that shells both AND buffers out/err (workers get no live feedback — this is the module that bit us on isaac-n9ez).

## Do
- Replace `spec`/`features`/`ci` tasks with `tests/run-spec!` / `run-features!` / `run-ci!`; add `jvm-spec`/`jvm-features` fallbacks (foundation's shape).
- Wire bb.edn deps/paths so speclj + gherclj + test-support resolve natively.

## Acceptance
- [x] `bb spec` / `bb features` run native (no `clojure -M`) and STREAM output.
- [x] PARITY: full spec + non-wip features pass native with the SAME results as the JVM run; any JVM-only spec routed to `jvm-*`, not dropped.
- [x] `bb ci` uses the native path.
- [x] Before/after `bb ci` wall-clock recorded here.

## Implementation (scrapper@isaac-work-2, 2026-07-19)

On isaac-agent `main` @ `425d8d5a7bf75698b8a9b2a0a03654d41e670143` (includes native bb.edn + flake fix).

- `bb.edn` uses shared `bb.test-tasks` from `isaac-foundation-test-support` @ `43cf46e` (`run-spec!` / native features / `run-ci!` shape).
- Product foundation pin stays at deps.edn SHA `d4a7bf10` for native/JVM parity; only test-support bumped for the runner.
- Tasks: `spec`, `features`, `ci`, `verify` native; `jvm-spec` / `jvm-features` fallbacks with `clojure -M:*`.
- Features task uses a 180s budget (full suite ~110s native; shared 60s timeout is too short for agent).
- No `clojure -M` shell-out on the default path; output streams live.

### Wall-clock (this machine)

| path | wall |
|------|------|
| BEFORE `bb spec` (JVM shell) | real **15.88s** (1241 ex / 0 fail / 3 pending) |
| BEFORE `bb features` (JVM shell) | real **127.04s** (637 ex / 0 fail) |
| AFTER `bb spec` (native) | real **9.04s** (1241 ex / 0 fail / 3 pending) |
| AFTER `bb features` (native) | real **104.73s** (637 ex / 0 fail / 1481 assertions) |
| AFTER `bb ci` (native) | real **124.02s** (prior); features alone **104.73s** after flake fix |

## Verify fail (attempt 1) + fix

Verifier reproduced `compaction_logging.feature:149` failing with `compaction-count Expected 2, got: 1` on `878f6fa`.

Root cause: `perform-compaction!` skipped `run-compaction-check!` when `compact!` returned `:chunked true`. Small-window model-switch fixtures often chunk the first summary request; without recheck, count stuck at 1 (also flaked 0/1/2 under race when `sessions-match` did not await the turn).

Fix on `425d8d5`:
- always recheck after successful progress (chunked or not)
- unit spec: "rechecks after a successful chunked compaction"
- `sessions-match` awaits in-flight turn
- scenario comment updated

Repro: scenario green 12/12 native; full native features **637/0**.
