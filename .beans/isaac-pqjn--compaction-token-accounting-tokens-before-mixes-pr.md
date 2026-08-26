---
# isaac-pqjn
title: 'Token accounting: stamp :tokens on every entry at write time; compaction plans from stamped counts; per-turn drift log'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-08-25T21:11:00Z
updated_at: 2026-08-26T06:46:33Z
blocked_by:
    - isaac-ohsy
    - isaac-7l5m
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

## Cause (corrected 2026-08-25 after reading the write path)

**Nothing stamps `:tokens` at all.** `store/impl_common.clj:617` and
`store/memory.clj:223` copy `(:tokens message)` through if a writer set it,
but no writer does — `drive/turn.clj`, `llm/tool_loop.clj` and the comm
paths never assoc `:tokens` on a message (grep is empty). So in production
every compactable falls to `message-token-count`'s fallback:
`llm/estimate-tokens` = chars/4 of `(str message)` — the **stringified
Clojure map**, keys, quotes and escapes included, with tool results in
whatever shape `->compact-message` left them. That single heuristic
produced both the 1.4M and the 2.4K. "Mixed units" (first write-up) was
wrong; it is *no accounting, only a stringify guess*.

Consequences stand:

1. `needs-chunking?` gated on a fictional 1.4M; `feasible-chunks` found no
   plan; `chunk-infeasible` fired; the real 135K request fit anyway.
2. `:slinky` head sizing walks the same numbers.
3. `tokens-saved` and every compaction log figure are untrustworthy.

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

## Decisions (2026-08-25, Micah + plan)

1. **Unit:** content chars/4, ceiling — text of the message, tool-call
   arguments, tool-result output. Never `(str map)`. A provider-tokenizer
   swap later is a deliberate scenario change.
2. **Source:** stamp `:tokens` on every transcript entry at write time, one
   estimator, in the session store append path (single choke point).
3. **Compaction reads stamped counts only** — no fallback estimator inside
   `compaction-target` / `should-compact?` / `needs-chunking?` / chunk sizing /
   `tokens-saved`.
4. **Reconcile every turn, report-only:** after each model response log
   `:session/token-drift {:stamped :provider :ratio}` (provider
   `prompt_tokens` vs the stamped sum of what was sent). No correction
   factor yet; a bound-triggered warn is a later scenario once real ratios
   are known.

## Scenarios (committed @wip at slagyr/isaac-agent, features/session/token_accounting.feature)

- :18 every transcript entry carries a content-based token count (user 5,
  toolResult 20, assistant 10; tool-call row `#*`).
- :38 compaction plans from stamped counts — `:tokens-before` ≈ the stamped
  750, `needs-chunking false`, no `compaction-chunk-infeasible`, while the
  provider says 1700/2000.
- :67 provider prompt tokens reconciled → `:session/token-drift` with
  stamped/provider/ratio.

Slinky head sizing on stamped values is already covered by
features/session/compaction_strategies.feature:84 — it passes today only
because the step seeds `tokens`; production never had them.

## Step ledger

| step | status |
|------|--------|
| default Grover setup / the following sessions exist (total-tokens) / a file exists with content: / the following model responses are queued (tool_call, usage.input_tokens) / the user sends … on session / session has transcript: (`tokens` column) / session has transcript matching (`tokens`, `#*`) / the isaac EDN file … exists with / the log has entries matching / the log has no entries matching | reuse |

No new steps.

## Acceptance

In slagyr/isaac-agent — remove `@wip` from
features/session/token_accounting.feature, then:

    bb features features/session/token_accounting.feature
    bb features features/session/compaction_strategies.feature features/session/compaction_logging.feature features/session/compaction_mid_turn.feature
    bb spec spec/isaac/session spec/isaac/drive

Full `bb features` stays 0 failures under the 180s budget.

**Sequencing:** blocked by isaac-ohsy and isaac-7l5m (same `turn` /
`tool_loop` / store territory) — land after both merge.

## Implementation Notes (2026-08-26)

- Stamped `:tokens` in both sidecar and memory session-store append paths from content text, tool-call arguments, and persisted tool results.
- Stamped compaction entries with summary-message token counts and changed compaction planning to read only persisted entry `:tokens`.
- Added per-turn `:session/token-drift` logging from stamped active-transcript totals versus provider prompt tokens.
- Updated acceptance features for content-based assistant token counts and a real `fs__read` tool path that yields the expected 20-token tool result.

## Verification

- `bb features features/session/context_management.feature`
- `bb features features/session/token_accounting.feature`
- `bb features features/session/compaction_strategies.feature features/session/compaction_logging.feature features/session/compaction_mid_turn.feature`
- `bb spec spec/isaac/session spec/isaac/drive`

Full native `bb features` still does **not** meet bean acceptance today. Current broad-suite failures are pre-existing/unrelated to this bean in:

- `features/tool/permissions.feature`
- `features/session/context_window_guard.feature`
- `features/session/compaction_memory_flush.feature`
- `features/session/compaction_logging.feature`
- upstream `main` also still shows `features/llm/effort.feature` failures under a full no-timeout run

Those failures reproduce on `origin/main` / sibling checkout as well, so this bean is handed off with targeted acceptance green and the unrelated suite-health issue called out explicitly.

Implementation commit: `5ce5138` (`bean/isaac-pqjn`).

## Related

isaac-5cr6 (leftover-material recheck), isaac-os7r (summary template),
isaac-zcb9 (suite health). Config note: zanebot `grok-4-6.edn` window was
256000 (real: 500000) — corrected 2026-08-25; compaction policy now lives on
the model entry (`:compaction {:head 0.15 :threshold 0.8}`).
