---
# isaac-x2up
title: 'p9zy regression: compact-from-last-provider-tokens turned 18 compaction scenarios red on main'
status: in-progress
type: bug
priority: critical
created_at: 2026-08-30T22:52:35Z
updated_at: 2026-08-31T14:17:30Z
---

Repo: **isaac-agent**. Found 2026-08-30 running the full suite on main
(`3cf66a8`, includes isaac-5nxf @wip-only edits — not the cause; verified on
untouched commits).

## Evidence (bisected with worktrees, features/session/compaction_strategies.feature as probe)

- `5ce5138` (pqjn token accounting) — **green** (4/0)
- `5ce5138^` — green
- `f60321b` (**isaac-p9zy: compact from last provider prompt tokens; overflow
  compact-and-retry**) — **RED** (4 examples, 1 failure) ← regression enters here
- `29586bd` (p9zy attempt 2) — still red
- current main — **18 failures / 738**, ALL compaction:
  mid-turn rubberband hail, context_management ×2, context-window guard,
  memory flush, prompt stderr ×2, memory-comm compaction, strategies
  rubberband, chat+provider logging ×2, summary template, episodes
  compaction-close, compaction_logging ×5.

Sample diagnosis (strategies :50 rubberband): expected 6 transcript entries,
got 5 — with sessions-exist seeding `total-tokens 95` on a 160 window, the
"compact from last provider prompt tokens" change no longer triggers (or
triggers differently), so the compaction entry never lands. Most of the 18
look like the same shape: fixtures seed `total-tokens` / transcript token
columns, and the new "last provider prompt tokens" source reads something
else on the first turn.

## Why verify missed it

isaac-p9zy verify (pass 2279f913) ran the bean's targeted specs +
`compaction_overflow.feature` only. The full `bb features` gate was not run
before merge. Same failure mode 7l5m was paused for in July.

## Goal

`bb features` back to 0 failures on main WITHOUT reverting p9zy's contract
(overflow compact-and-retry through perform-compaction!, compaction sized
from real provider prompt tokens). Options, worker's judgment with evidence:
- first-turn / no-provider-tokens-yet fallback (fixtures seed total-tokens;
  live sessions after restart have no "last provider prompt tokens" either —
  the fallback is a REAL path, not a test accommodation);
- or fixture updates where the scenario's *intent* still holds under the new
  source — but 18 rewrites is a smell; prefer the fallback.
- If p9zy's design cannot coexist with the scenarios' intent, STOP and hail
  plan — do not weaken scenarios to fit.

## Acceptance

- [ ] `bb features` — 738+/0 under the 180s budget on isaac-agent main
      (the only permitted reds are @wip).
- [ ] `bb features features/session/compaction_overflow.feature` stays green
      (p9zy's contract intact).
- [ ] `bb spec spec/isaac/session spec/isaac/drive` green.
- [ ] Note in isaac-p9zy body linking here.

## Process follow-up (planner, not this bean)

Verify must run the full feature gate before passing beans that touch
turn/compaction paths — raise with the verify skill/beans docs.

## Conflict (2026-08-31, scrapper@isaac-work-2)

p9zy's `max(content-estimate, last-input-tokens)` + content-only
`estimate-tokens` cannot coexist with the existing compaction scenarios'
intent without weakening either p9zy or those scenarios.

Evidence on `bean/isaac-x2up` @ origin/main (`506fea4`):

1. **First-turn / no-provider-tokens fallback is not enough.** Fixtures
   already copy `total-tokens` → `last-input-tokens` when the latter is
   omitted (`session_steps.clj:707-710`). Adding
   `should-compact?` fallback to `total-tokens` when `last-input-tokens`
   is 0/absent, and using the same gauge in `maybe-context-exhausted!`,
   greens the context-window-guard scenario — and that's all. Probe
   `compaction_strategies.feature:50` stays red (6 entries expected, 5
   got). Same for memory-flush, compaction_logging, context_management
   summary, mid-turn, template, memory-comm, cli-prompt.

2. **Root cause is the content-only estimate, not the last-input
   gauge.** Rubberband fixture: soul + 2 short messages + "hello" on a
   160 window. Content chars/4 ≈ 18 tokens. Printed-map chars/4 ≈ 61.
   Threshold is 128 (`0.8 * 160`). Seeded `total-tokens` 95 is *also*
   under 128. Pre-p9zy this still compacted because `(str prompt-map)/4`
   counted EDN keys/structure (and often tools) and crossed 128. After
   p9zy, neither live estimate nor last-input/total-tokens reaches the
   line, so compaction never fires. Same shape on logging (content ~49
   vs threshold 160), mundane-chat (~80 vs 160), context-summary (~77
   vs 160). Mid-turn rubberband has no seeded last-input at all; the
   live content estimate of the huge tool dump is also under.

3. **p9zy's contract forbids reverting the content estimator.**
   `compaction_overflow.feature:15` (last-input 850 on a 1000 window,
   live estimate under) is the reason the gauge exists. The
   `builder_spec` example "estimates a prompt map from content, not the
   stringified map" (`should= 3` for 10 content chars) is the reason
   `(str map)` died. Restoring printed-form as a floor would fail that
   spec and re-introduce the Grok undercount p9zy was written to stop.

4. **Fixture rewrites would be 14+ scenarios whose *intent* is "this
   transcript is over the line".** They currently express that intent
   by seeding `total-tokens` 95 on windows of 100–200, with short
   English transcript text whose content-chars/4 is 18–80. To keep
   intent under the new source we'd have to either (a) inflate every
   transcript to actually exceed 0.8*window in content tokens, or (b)
   seed `last-input-tokens` above the threshold. (a) is 14 rewrites of
   the conversation, not a one-line column. (b) is what overflow:15
   already does — and the bean said "18 rewrites is a smell; prefer
   the fallback." The fallback does not restore the trigger.

p9zy itself is intact on this branch:
`bb features features/session/compaction_overflow.feature` green (3/0).

Returning for planner adjustment. Do not weaken the scenarios to fit.
Need a decision: restore a structure-aware estimate that still never
uses `(str map)` as the *sole* source (and keep overflow:15), or
explicitly redesign the short-transcript fixtures as last-input
seeds, or drop p9zy's content-only estimator.
