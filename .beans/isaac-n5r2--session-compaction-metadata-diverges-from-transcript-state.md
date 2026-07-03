---
# isaac-n5r2
title: last-input-tokens stores turn-cumulative sum — compaction gauge lies (compaction itself is correct)
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-03T06:20:00Z
updated_at: 2026-07-03T18:16:42Z
---

## Investigation findings (2026-07-03, planner — supersedes original problem statement)

Compaction is working correctly. The reporting is what lies.

1. **Trigger is sound.** `run-compaction-check!` uses a live per-request prompt estimate (`compaction/estimate-prompt-tokens` over the actual transcript), threshold 0.8×window. July 2 log evidence: `isaac-verify` measured 154,097 / 278,528 (55%) — correctly below threshold, correctly no compaction. `orchestration-plan` 60k — same.
2. **The "inconsistent" session is consistent.** `orchestration-verify.jsonl` DOES contain a `"type":"compaction"` entry (original grep shape missed it); its sidecar `:effective-history-offset 1041977` exactly equals the splice backup byte size (`orchestration-verify.2026-07-02T18:30:30.bak.jsonl`). Count 1, entry 1, offset exact.
3. **The real bug:** the tool loop (`isaac.llm.tool-loop/run`, `merge-with +`) sums input tokens across every request in a turn, and `drive/turn.clj` stores that TURN-CUMULATIVE sum into `:last-input-tokens`. A 14-request turn at ~150k prompt each → "2.2M last input tokens" → `sessions list` PCT shows 800%+ → operators conclude compaction is broken.

The existing scenario "last-input-tokens is updated from response usage on every turn" states the correct intent ("replaced, not added... reflects current conversation size") but only covers single-request turns, where sum == last.

## Fix (approved by Micah 2026-07-03)

- `:last-input-tokens` ← the **final** request's input tokens (matches its name and the compaction-gauge purpose).
- The whole-turn sum moves to a new field `:turn-input-tokens`.
- Cumulative `:input-tokens` keeps summing as today.
- `sessions list` PCT needs no change — it becomes truthful via the storage fix.

## Acceptance scenarios (committed @wip, 2026-07-03)

`isaac-agent features/session/context_management.feature` — "@wip Scenario: a multi-request tool-loop turn stores the final request's input tokens, not the turn sum". Dry-run reproduces the bug precisely (Expected 120, got 220; turn-input-tokens nil).

Acceptance: remove @wip; `bb features features/session/context_management.feature` green; `bb spec` green. Session schema gains `:turn-input-tokens` (system-managed).

## Likely repo scope

isaac-agent (`drive/turn.clj` token persistence, `session/schema.clj`). The tool loop already returns per-request usage on the final response — no tool-loop change expected.

## Related

- isaac-a9vf (sessions list size-on-disk column) — complementary observability.
- Original divergence observations retained in git history of this bean file.


---

## Resolution (unverified — for verifier)

Implemented in `isaac-agent` **main** commit **e098ac1**.

### What changed

- `drive/turn.clj`
  - preserved whole-turn accumulated usage for cumulative billing fields.
  - stored `:last-input-tokens` from the **final request usage block** (`response :usage`) instead of the tool-loop sum.
  - stored the whole-turn accumulated prompt sum in new session field `:turn-input-tokens`.
- `session/schema.clj`
  - added system-managed `:turn-input-tokens`.
- session stores / defaults
  - initialized and defaulted `:turn-input-tokens` in memory and sidecar-backed sessions.
  - updated sidecar token persistence helpers and spec helpers so explicit updates can set both `:turn-input-tokens` and `:last-input-tokens` independently.
- tests
  - removed `@wip` from the committed acceptance scenario in `features/session/context_management.feature`.
  - added/updated specs covering multi-request persistence and sidecar behavior.

### Verified

- `bb lint src/isaac/drive/turn.clj src/isaac/session/schema.clj src/isaac/session/store/memory.clj src/isaac/session/store/impl_common.clj src/isaac/session/store/sidecar.clj`
  - 0 errors, 3 pre-existing warnings (`unused binding session-key`; unused requires in `session/store/memory.clj`).
- `bb spec`
  - green: **1137 examples, 0 failures, 2236 assertions**.
- `clojure -M:features features/session/context_management.feature`
  - green: **11 examples, 0 failures, 12 assertions**.

### Notes

- `bb features` / full `clojure -M:features` is currently red on this repo **before and after** this fix due to unrelated baseline feature failures:
  - three `features/session/cli.feature` session-list layout expectations still fail from the already-landed SIZE-column change (`isaac-a9vf` lineage), and
  - one unrelated `cancel_aborts_in_flight_turn_work` feature is also red on current main.
- This bean's committed acceptance target was the focused `context_management.feature` scenario plus `bb spec`; both now pass.
