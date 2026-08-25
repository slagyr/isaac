---
# isaac-pqjn
title: 'Compaction token accounting: :tokens-before mixes provider usage with chars/4 estimates; chunk gating runs on fiction'
status: draft
type: bug
priority: high
created_at: 2026-08-25T21:11:00Z
updated_at: 2026-08-25T21:11:00Z
---

Likely repo: **isaac-agent** (`isaac.session.compaction`, `isaac.session.store.*`,
`isaac.llm.api.protocol/estimate-tokens`). Found 2026-08-25 watching
isaac-work-2 (scrapper, grok-4.6) during isaac-ohsy.

## Evidence (zanebot server.log, session isaac-work-2, 2026-08-25)

| time  | total-tokens (provider) | history-entries | compact-count | :tokens-before | :summary-prompt-tokens | needs-chunking |
|-------|------|-----|-----|-----------|---------|------|
| 19:00 | 128,347 | 197 | 100 | **1,411,605** | 135,131 | true → chunk-plan: 0 chunks, failure nil → `compaction-chunk-infeasible` |
| 19:16 | 130,332 | 245 | 123 | **2,440**     | 138,563 | false |
| 20:13 | 129,328 | 279 | 140 | **2,997**     | 142,476 | false |
| 20:48 | 129,597 | 419 | 210 | **4,846**     | 158,808 | false |

Same kind of history each time; `:tokens-before` swings from 1.4M (11× the
provider's own count of the whole context) to 2.4K (a hundredth of the
request that was actually built from those same messages).

## Cause (read of the code, not yet proven by a spec)

`compaction-target` sums per-compactable `:tokens`. Each compactable's
`:tokens` comes from `message-token-count` = `(or (:tokens entry)
(llm/estimate-tokens {:messages [message]}))`, and tool pairs add the two
entries' `:tokens` with nil→0. So a single sum mixes:

- provider-stamped `:tokens` on entries that have one (copied through by
  `store/impl_common.clj:617` from `(:tokens message)` — whatever unit the
  writer used, typically per-message usage), and
- chars/4 of `(str message)` for entries that don't (`protocol/estimate-tokens`
  stringifies the whole Clojure map — keys, quotes, escapes — so a large
  tool result over-counts, and an EDN-stringified map with escaped newlines
  over-counts more).

Nothing in `drive/turn.clj` or `tool_loop.clj` stamps `:tokens` on entries
at all (grep is empty), so the only stamped values come from wherever comm
or compaction writers put them — which is why the number collapses to a few
thousand once the history is mostly compaction-era entries.

Consequences:

1. `needs-chunking?` = `(or (> tokens-before context-window) (> summary-prompt-tokens context-window))`
   fired at 19:00 on the fictional 1.4M; `feasible-chunks` produced zero
   chunks; the code logged `chunk-infeasible` and fell through to a normal
   135K request that happened to fit. With a real over-window history the
   same path would fail for real.
2. `:slinky` `compaction-target` walks entries by `:tokens` to size the kept
   head — with 2.4K-for-123-entries it keeps nearly everything or nothing.
3. Every log line and `tokens-saved` number derived from these is untrustworthy.

## Goal

**One unit, one source.** Per-entry token counts used for compaction
planning must be (a) present on every entry, (b) in the same unit as
`context-window` and the provider's usage, and (c) reconciled against the
provider's reported prompt tokens on every turn so drift is visible.

Design sketch (planner, to be scenarioed):

- Stamp `:tokens` on every transcript entry at write time from one estimator
  that measures **content**, not `(str map)` — text chars/4 (or the
  provider's tokenizer where available), tool results by their output text.
- After each LLM response, reconcile: provider `prompt_tokens` vs the sum of
  stamped entries in the prompt; log `:session/token-drift` with the ratio;
  keep a per-session correction factor if drift exceeds a bound.
- `compaction-target`, `should-compact?`, `needs-chunking?`, chunk sizing
  and `tokens-saved` all read the stamped values only — no fallback
  estimator inside compaction.
- Scenarios: a transcript with mixed stamped/unstamped entries yields one
  `:tokens-before` within N% of the built request's estimate; chunk gating
  never trips when the provider's own total is under the window; slinky head
  sizing holds the configured head fraction.

## Acceptance

Draft until scenarios are written (`/plan-with-features`). Hold: do not
touch during isaac-ohsy / isaac-7l5m rebase — both are in `tool_loop` /
`turn` territory.

## Related

isaac-5cr6 (leftover-material recheck), isaac-os7r (summary template),
isaac-zcb9 (suite health). Config note: zanebot `grok-4-6.edn` window was
256000 (real: 500000) — corrected 2026-08-25; compaction policy now lives on
the model entry (`:compaction {:head 0.15 :threshold 0.8}`).
