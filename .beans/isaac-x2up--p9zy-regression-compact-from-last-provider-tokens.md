---
# isaac-x2up
title: 'p9zy regression: compact-from-last-provider-tokens turned 18 compaction scenarios red on main'
status: todo
type: bug
priority: critical
created_at: 2026-08-30T22:52:35Z
updated_at: 2026-08-30T22:52:35Z
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
