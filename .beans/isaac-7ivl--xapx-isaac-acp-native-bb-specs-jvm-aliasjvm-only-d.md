---
# isaac-7ivl
title: 'xapx: isaac-acp — native bb specs (JVM-alias/JVM-only deps)'
status: in-progress
type: task
priority: normal
created_at: 2026-07-19T17:10:52Z
updated_at: 2026-07-19T18:34:28Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-acp** to native bb specs via the shared runner.

## Wrinkle
Its bb.edn comment says tests run via JVM clojure aliases "matching" deps.edn — a hint it may lean on JVM-only deps. Investigate first: try native and see what fails. Anything genuinely JVM-only stays on `jvm-spec`/`jvm-features` and is documented; the rest goes native.

## Acceptance
- [x] `bb spec` / `bb features` native for the specs that CAN run native (no `clojure -M`), streamed.
- [x] PARITY: native + `jvm-*` together == the old JVM results; JVM-only specs documented, not dropped.
- [x] `bb ci` native path; before/after wall-clock recorded here.

## Implementation (scrapper@isaac-work-2, 2026-07-19)

Committed directly to `main` on isaac-acp @ `1a81c6c2ec108648ba6bed291ff399943a224377`.

- bb.edn uses shared `bb.test-tasks` from isaac-foundation-test-support @ `43cf46e`.
- Product foundation pin bumped `a834445` → `d4a7bf10` for test-support/cli_steps color API parity; test-support stays at `43cf46e` for the runner.
- `:spec`/`:features`-equivalent classpath inlined: agent + agent-spec, server + server-test-support, foundation-spec, marigold bridge/longwave, speclj, gherclj, babashka.process.
- Features keep the **explicit step-ns list** from deps.edn main-opts (not the default `isaac.**-steps` glob) and a 180s budget.
- Tasks: `spec`, `features`, `ci`, `verify` native; `jvm-spec` / `jvm-features` fallbacks honor `:dev-local` when `../isaac-agent` exists (`ISAAC_GIT=1` forces pins).
- **No JVM-only specs found.** The old "tests run via JVM clojure aliases" comment was layout (matching cron/hail shell-out), not a JVM-only dep constraint. Full suite passes native with the same pending counts as JVM.

### Wall-clock (this machine, ISAAC_GIT=1 baseline)

| path | wall |
|------|------|
| BEFORE bb spec (JVM shell) | real **11.68s** (70 ex / 0 fail / 1 pending) |
| BEFORE bb features (JVM shell) | real **83.18s** (61 ex / 0 fail / 5 pending) |
| AFTER bb spec (native) | real **7.18s** (70 ex / 0 fail / 1 pending) |
| AFTER bb features (native) | real **16.20s** (61 ex / 0 fail / 5 pending) |
| AFTER bb ci (native) | real **14.40s** |

## Verify fail (attempt 1, 2026-07-19): isaac-acp native/JVM suites still have pending scenarios (1 spec, 5 features), so the acceptance gate is not green and the bean cannot pass.

## Verify fail resume (attempt 1) — worker assessment

Verifier is correct that the suite reports pendings. Diagnosis: **these pendings are pre-existing product debt, not a runner regression.**

### Spec pending (1)
`spec/isaac/comm/acp/server_spec.clj:398` — explicit `(pending "investigating snapshot capture in with-nested-system scope")` since **2026-05-21** (commit db83cb30, Micah). Comment documents it as a pre-existing migration symptom left pending so it does not block CI.

### Feature pendings (5) — gherclj "not yet implemented"
1. ACP command tool notifications arrive before the final response in stdout
2. ACP command acp uses workspace SOUL.md when no soul in crew config
3. ACP Provider Error Surfacing connection refused error is surfaced to the client
4. ACP Turn Cancellation session/cancel arrival is logged at info
5. ACP Error Response Format connection refused error is sent as agent_message_chunk with end_turn

These lack step implementations. Native and JVM report **identical** pending counts (bean wall-clock table already records BEFORE JVM = 1+5 pending, AFTER native = 1+5 pending). **Parity holds.**

### Recommendation
This bean is a **runner conversion** (xapx). Completing these scenarios is product work outside the conversion. Request planner rescope: accept parity with pre-existing pendings (same as old JVM gate), or spawn product children and keep 7ivl blocked. Worker will not re-mark unverified until scope is clarified.
