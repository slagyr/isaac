---
# isaac-x27m
title: cancel_aborts_work:32 flaky/red — session cancel state nil where 'cancelled' expected
status: todo
type: bug
priority: normal
created_at: 2026-07-12T23:19:23Z
updated_at: 2026-08-25T04:19:53Z
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
