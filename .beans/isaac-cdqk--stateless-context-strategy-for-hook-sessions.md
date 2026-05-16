---
# isaac-cdqk
title: context-mode field on crew config
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-05-15T21:42:03Z
updated_at: 2026-05-16T03:38:11Z
---

## Problem

Hook sessions accumulate unbounded JSONL history, and every turn replays the entire transcript into the model. Three observable failure modes from today on zanebot:

1. **Pinky still checks `AGENDA.md` on every location firing** — old template behavior embedded in prior turns wins over the new template.
2. **Pinky adds `## Section` headers and bullets** despite the new templates asking for flat tagged lines — replayed file structure is conditioning the format.
3. **Each hook firing uses 2-3 tool calls instead of 1.** Token cost compounds — location hit 56k tokens in this morning's burst.

Hook turns are *independent*. Each is "here's a fresh datapoint, log it." There's no model-side benefit to replaying any prior turn — the durable memory file is the cross-turn state, the JSONL transcript is just a log for humans.

## Design

A new crew-level field `:context-mode` controls how much transcript history reaches the model per turn:

| Value | Behavior |
|---|---|
| `:full` (default) | Replay full transcript (subject to compaction). Current behavior, unchanged. |
| `:reset` | Send only soul + current user message. Transcript on disk is preserved. |

`:context-mode` is **distinct from compaction** — compaction shrinks history when it's too big, `:context-mode` controls whether history is loaded at all. With `:reset`, compaction never has cause to fire on that session.

### Where it lives

- **Crew config only.** No per-session override. (Per-session compaction strategy in the existing slinky test is a fixture quirk we don't replicate here.)
- Field shape: `:context-mode :full | :reset` at the top level of the crew map.
- Schema validation rejects unknown values.

### On transcript growth

`:reset` doesn't compact the on-disk JSONL — it keeps growing turn by turn. That's fine for now: largest hook log on zanebot is 56KB after months, JSONL is line-oriented, disk is irrelevant. Log rotation is a separate concern, separate bean, only if it ever bites.

## Feature spec

`features/session/context_mode.feature` (committed with `@wip`):

| # | Scenario | Line |
|---|---|---|
| 1 | `:context-mode :reset` replays no history — Pinky greets each turn fresh | features/session/context_mode.feature:13 |
| 2 | default context-mode (`:full`) replays prior history | features/session/context_mode.feature:54 |
| 3 | Unknown `:context-mode` value is rejected | features/session/context_mode.feature:85 |

Zero new step definitions required — all assertions reuse existing steps.

## Acceptance

- [ ] `:context-mode` field accepted at the top level of `:crew/<id>` config
- [ ] Validation rejects values other than `:full` and `:reset`
- [ ] `:full` is the default when the field is omitted (preserves current behavior for all chat crews)
- [ ] `:reset` sends only soul + current user message to the provider — no prior transcript entries
- [ ] On-disk transcript is appended every turn regardless of mode
- [ ] All three `@wip` scenarios pass; remove `@wip` tag from `features/session/context_mode.feature`
- [ ] Run: `bb features features/session/context_mode.feature` — green
- [ ] Set pinky → `:context-mode :reset` in zanebot config (operational follow-up, after implementation lands)
- [ ] Smoke-test with a Zaap burst: pinky should drop the AGENDA-check habit, follow new templates, ~1 tool call per firing

## Out of scope

- Compacting the on-disk JSONL — separate concern, separate bean if growth ever bites
- Per-session `:context-mode` override — crew-only intentionally
- Cron/webhook context-mode for non-hook channels — same machinery would apply but not exercised here
- Status command surfacing `:context-mode` — nice-to-have, defer

## Sequencing

- [x] Draft gherkin scenarios — landed in `features/session/context_mode.feature` with `@wip`
- [x] Design decision: `:context-mode` as a crew field, distinct from compaction
- [ ] Promote bean from `draft` → `todo` once the funnel bean (isaac-p7k1) lands or is unblocked

Implementation order when active:

- [ ] Add `:context-mode :full | :reset` to crew schema; validate unknown values
- [ ] Thread `:context-mode` through the turn pipeline (after isaac-p7k1 lands, the funnel is the natural seam)
- [ ] Turn pipeline respects `:reset`: skip transcript loading; system + current user only
- [ ] Remove `@wip` from `features/session/context_mode.feature`; confirm green
- [ ] Update pinky on zanebot

## Related

- **isaac-p7k1** (turn-building funnel) — the funnel is the natural place to consult `:context-mode`; this bean should land **after** p7k1
- Pinky allowlist + hook templates (already shipped to zanebot) — those fixes only fully stick once `:reset` is in; until then, replayed history overrides the new template behavior
