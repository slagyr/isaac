---
# isaac-xapx
title: bb spec/features run native in babashka, not shell out to clojure
status: completed
type: epic
priority: normal
created_at: 2026-07-15T17:38:39Z
updated_at: 2026-07-19T23:48:54Z
---

## Goal (Micah, 2026-07-15; scope widened 2026-07-19)

`bb spec` / `bb features` / `bb ci` in **every isaac module repo** (not just
isaac-agent) must run speclj/gherclj **natively in babashka**, NOT shell out to
`clojure -M:spec`. Native bb is far faster (no JVM cold-start) and streams
output; shelling clojure throws away bb's whole speed advantage. "bb should not
shell out to clojure."

## Evidence

isaac-agent `bb.edn` `spec` task: `(apply sh/sh "clojure" "-M:spec" *command-line-args*)` — a thin wrapper around JVM clojure. `features` and `ci` likewise shell `clojure -M:features` / `-M:spec`. So `bb spec` == `clojure -M:spec` + bb boot + OUTPUT BUFFERING (the task buffers out/err and prints at the end, so workers watching get no live feedback — a worker on isaac-n9ez visibly abandoned `bb spec` for `clojure -M:` for exactly this reason). The same shell-out pattern is repeated across most module repos (survey below).

## The pattern to copy — isaac-foundation already does this right

foundation `bb.edn` uses a `bb.test-tasks` namespace:
- `spec`     -> `(tests/run-spec! …)`      — NATIVE bb speclj
- `features` -> `(tests/run-features! …)`  — NATIVE bb gherclj
- `ci`       -> `(tests/run-ci!)`          — native
- `jvm-spec` -> `(tests/run-jvm-spec! …)`  — explicit JVM FALLBACK (kept for specs that genuinely need the JVM)
- `jvm-features` -> `(tests/run-jvm-features! …)`

## Module survey (2026-07-19)

**Shell out to `clojure` today — need the fix:**

| Module | Current spec/features tasks | Wrinkle to preserve |
|---|---|---|
| isaac-agent | `sh/sh clojure -M:spec` / `-M:features` | the one that bit us; also buffers output |
| isaac-acp | `shell clojure <alias>:spec` / `:features` | bb.edn comment claims tests run via JVM aliases "matching" — check for JVM-only deps |
| isaac-server | `shell clojure -M:test:spec` / `-M:test:features` | `:test` alias adds classpath — ensure spec-support/test deps are on the bb classpath |
| isaac-cron | `shell clojure -M:spec` / `-M:features` | — |
| isaac-hail | `shell clojure -M:spec` / `-M:features` | — |
| isaac-hooks | `shell clojure -M:spec` / `-M:features` | — |
| isaac-discord | `shell clojure -M:dev-local:spec` / `-M:spec` (`dev-local?` switch) | preserve the dev-local alias behavior |
| isaac-imessage | `shell clojure -M:spec` / `-M:features` | — |

**Already native — verify only, no change expected:** isaac-cli-proxy, isaac-cli-server (bb.edn comment: "Babashka runs specs/features directly (no clojure subprocess)").

**Reference — do NOT touch:** isaac-foundation (owns `bb.test-tasks`).

## Key design decision — SHARE the runner, do not copy-paste it 8×

`bb.test-tasks` lives at `isaac-foundation/bb/test_tasks.clj`, on foundation's
own bb classpath only (foundation's `:paths` includes `"."`). Other modules
depend on isaac-foundation via git but do NOT get `bb/test_tasks.clj` on their
classpath — so they cannot `:require ([bb.test-tasks :as tests])` today.

The DRY fix is to **re-home the runner into a shared, dependable location** —
foundation's test-support (`spec-support` `deps/root`), which every module
already depends on as `isaac-foundation-test-support` — then each module's
bb.edn just does `:requires ([bb.test-tasks :as tests])` and calls
`run-spec!` / `run-features!` / `run-ci!`. **One implementation, N consumers.**
Copy-pasting the runner into each repo is the wrong move — it will drift.

Resolve at spec time: exact home for `test_tasks.clj` (spec-support vs a new
shared `deps/root`), and confirm bb loads it via the test-support classpath in a
consumer repo before converting all eight.

## Suggested breakdown (this bean is the parent/epic)

1. **Shared-runner re-home** (blocks the rest): expose `bb.test-tasks` via
   test-support; prove a consumer can `:require` it.
2. **isaac-agent** (the one that bit us) — convert + parity.
3. **Trivial sweep** — cron / hail / hooks / imessage (identical plain
   `-M:spec` / `-M:features`), one batch.
4. **Wrinkled ones, separately** — server (`:test` alias), discord
   (`dev-local` switch), acp (JVM-alias / possible JVM-only deps).
5. **Verify-only** — cli-proxy, cli-server already native; confirm parity.

## Acceptance (parity is the gate — do not blind-flip; per module)

- [ ] The shared `bb.test-tasks` runner resolves for consumer repos (not copy-pasted per repo).
- [ ] For each module in the "need the fix" table: `bb spec` and `bb features` run natively (no `clojure -M` shell-out) and STREAM output.
- [ ] PARITY per module: the FULL spec + non-wip feature suite passes natively with the SAME results as the JVM run. Any spec/feature that genuinely requires the JVM is moved to (or documented against) `jvm-spec`/`jvm-features`, not silently dropped.
- [ ] `bb ci` (the verification gate) uses the native path in every converted module.
- [ ] Record before/after wall-clock for `bb ci` per module in the bean notes (the speed win is the point).
- [ ] cli-proxy / cli-server confirmed already-native (parity spot-check).

## Risk

Some specs may use JVM-only interop and fail natively — that is EXPECTED and is what `jvm-spec`/`jvm-features` are for. The per-module parity check surfaces them; route those to the JVM task rather than forcing them native. The wrinkled modules (server/discord/acp) are the likeliest to hold JVM-only specs.

## Summary of Changes (2026-07-19)

**Goal achieved: `bb spec`/`features`/`ci` run native in babashka across the isaac module fleet — no more shelling out to `clojure -M`.**

- **x5ru** — re-homed the runner into foundation `spec-support` (`bb.test-tasks`), shared by all modules; pilot proven on isaac-hail. Merged to foundation main `43cf46e`.
- Converted + verified + green CI on main: **isaac-agent** (h5xm), **isaac-server** (ykuf), **isaac-discord** (fvzo), **isaac-acp** (7ivl), **isaac-cron/hooks/imessage** (dt9h), **isaac-hail** (x5ru pilot).
- **fq28** — confirmed isaac-cli-proxy / isaac-cli-server were already native.
- All landed directly on each module's main (no branches, per operator directive); the ci-failure orchestration self-healed one foundation-pin skew along the way.

**Follow-ups reparented OUT** (test-completeness debt surfaced, not caused, by the conversion — not part of this epic's scope): isaac-3c3z (imessage JVM feature suite), isaac-iz35 (hooks pending scenarios), isaac-h568 (acp pending scenarios).
