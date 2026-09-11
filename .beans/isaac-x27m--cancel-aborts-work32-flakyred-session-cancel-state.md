---
# isaac-x27m
title: cancel_aborts_work:32 flaky/red — session cancel state nil where 'cancelled' expected
status: completed
type: bug
priority: normal
created_at: 2026-07-12T23:19:23Z
updated_at: 2026-08-26T00:02:27Z
---

## Bug

`features/bridge/cancel_aborts_work.feature:32` — scenario "session remains
usable after a cancel mid-loop" — fails: `Expected: "cancelled" got: nil`.

Reproduced isolated by verify (perceptor) on `isaac-agent` main @
`5d8a51d73ea789f2ff05ae50027bec572191aa64`:
`clojure -M:features features/bridge/cancel_aborts_work.feature:32`
-> `1 examples, 1 failures, 1 assertions`.

## Why this is its own bean (split from isaac-l70j)

Surfaced while verifying isaac-l70j (claude-cli token usage). It is unrelated
to l70j's change surface: l70j touches `src/isaac/llm/claude_cli*` and
`features/llm/api/claude_cli.feature` only; it does not touch the bridge/cancel
path. The failing feature is on `main` independent of l70j, so it must not
gate or reopen l70j. l70j's own contract is verified green (see that bean).

## To determine

- Whether this is a consistent regression or a flaky/timing-sensitive
  assertion (`await-condition` on the cancel state) — run it several times in
  isolation and under the full `clojure -M:features` suite.
- If consistent: the cancel-mid-loop path leaves session cancel state `nil`
  where the scenario expects `"cancelled"`.

## Acceptance

- [ ] Root-caused: regression vs. flake, with evidence.
- [ ] `clojure -M:features features/bridge/cancel_aborts_work.feature:32` green,
      stably, in `isaac-agent`.
- [ ] Full `clojure -M:features` green.


## Additional evidence (2026-08-25, from isaac-zcb9 verify-fail 2)

On isaac-agent origin/main@69679f2 (zcb9 suite-health landed):

- Full `bb features` intermittently failed at `features/bridge/cancel_aborts_work.feature:27` — `Then the turn result is "cancelled"` got `nil`.
- Isolated scenario/file reruns passed repeatedly; later full suite + `bb ci` also passed on the same SHA.
- Same symptom family as this bean's original `:32` case. zcb9 planner split: `@wip` the intermittent cancel scenarios under this bean; zcb9 no longer gates on cancel flake once isolated.


## Isolation (2026-08-25, scrapper@isaac-work-1)

Both cancel scenarios in `features/bridge/cancel_aborts_work.feature` are now `@wip` with comments pointing at this bean:

- "cancel between tool-loop iterations skips the next chat call" (was :20; Then at :27 was the full-suite flake)
- "session remains usable after a cancel mid-loop" (original :32; same cancelled-vs-nil contract)

zcb9 no longer gates on these. Product fix remains this bean.

## Implementation (2026-08-25, scrapper@isaac-work-1)

Root cause: `tool-loop/run` stops after tools with `{:cancelled? true :response nil}` (no `:error`). `execute-llm-turn!` only treated `:error :cancelled`, `cancelled-response?`, or a still-live `bridge/cancelled?` as cancel. After `finalize-turn-result` the `:cancelled?` map fell through to `process-response!`, so `Then the turn result is "cancelled"` saw `nil`. Isolated runs often still had the live cancel flag; full-suite timing cleared it first — flake.

Fix: treat `(:cancelled? result)` as cancelled in the post-loop cond, same as `:error :cancelled`. Un-wip both `cancel_aborts_work` scenarios.

Evidence:

- Unit: `spec/isaac/drive/turn_spec.clj` — "returns stopReason cancelled when the tool-loop stops with :cancelled? after tools"
- Isolated `bb features features/bridge/cancel_aborts_work.feature` 2/0 × 5
- `clojure -M:features` cancel+suspend 8/0/20
- Full JVM `clojure -M:features` **737/0/1963 in 89.8s**

Landed isaac-agent `origin/main` = `59de03b52ca4957842c33b151a7bf7877eaecf8f`
