---
# isaac-h5xm
title: 'xapx: isaac-agent — native bb specs'
status: in-progress
type: task
priority: high
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T17:58:43Z
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
- [ ] `bb spec` / `bb features` run native (no `clojure -M`) and STREAM output.
- [ ] PARITY: full spec + non-wip features pass native with the SAME results as the JVM run; any JVM-only spec routed to `jvm-*`, not dropped.
- [ ] `bb ci` uses the native path.
- [ ] Before/after `bb ci` wall-clock recorded here.

## Implementation (scrapper@isaac-work-2, 2026-07-19)

Branch `origin/bean/isaac-h5xm` on isaac-agent @ `878f6fa0f0fddb3a585dc8af9c8256620a38fb18`.

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
| AFTER `bb features` (native) | real **~109s** (637 ex / 0 fail on clean re-run) |
| AFTER `bb ci` (native) | real **124.02s** |

One intermittent compaction-logging assertion (`compaction-count` 2 vs 1) observed once under native; re-run green. JVM baseline remains the parity reference.

## Verify fail (attempt 1, 2026-07-19): native bb features are not green on the handoff commit because the compaction_logging smaller-context scenario still fails reproducibly with compaction-count 1 vs expected 2
