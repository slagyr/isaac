---
# isaac-n5r2
title: last-input-tokens stores turn-cumulative sum — compaction gauge lies (compaction itself is correct)
status: todo
type: bug
priority: high
created_at: 2026-07-03T06:20:00Z
updated_at: 2026-07-03T15:01:38Z
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
