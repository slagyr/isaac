---
# isaac-t0hh
title: 'Held work beans are invisible: a work turn ending :reply with the bean still in-progress and no handoff must be re-delivered by the hail worker (bounded) and the model told its real cycle limit'
status: todo
type: bug
priority: high
tags:
    - hail
    - durability
created_at: 2026-09-19T03:36:17Z
updated_at: 2026-09-19T03:36:17Z
---

## Problem (zanebot, 2026-09-18/19 — six beans in one day)

A hail work turn ends with the bean still `in-progress`, no `unverified` handoff, no conflict hail, no escalation — and nothing notices. The delivery is `:delivered`, the session is idle, the planner finds out by reading transcripts. Today: x2lp, jkx7, at5m, lsz2, nqeq (re-hailed by hand at 23:43Z) and 4sqh (claimed 02:41Z, stopped 02:45Z, re-hailed by hand 03:36Z after 50 idle minutes).

The 4sqh transcript shows the model's reason: "Wrap-up. Cycle budget exhausted; work checkpointed." The turn's real state: `:turn/ended :ended-by :reply :cycle-limit 250` after ~20 cycles. The model invented an exhausted budget, checkpointed, and stopped — the skill's "HOLD + human escalate" became "hold, silently". Cause on the prompt side: the band template and skill say "the cycle budget comes from config" without stating it, so the model guesses conservatively. Cause on the infrastructure side: no one owns a held bean.

## Fix (two halves; both)

1. **The delivery worker continues held work — bounded.** When a hail work turn ends `:reply` and the bean it was dispatched for is still `in-progress` with neither the `unverified` tag nor a plan-band/escalation hail sent during the turn, the delivery is NOT complete: log `:hail/work-held :bean … :continuations n`, and re-deliver the same hail to the same session (`reply_to` the original) after a short delay (30 s), up to `:hail :max-continuations` (default 5). Past the cap: `:hail/work-abandoned` + attention post naming the bean and the last checkpoint. This is infrastructure re-delivery, not the model hailing itself — the skill's "never hail yourself" rule stands. Bean state is the signal: the worker reads it from the bean file (`beans show` / the .beans clone the crew already pulls).
2. **Tell the model the truth about its budget.** The work band template / skill state the configured cycle limit ("this turn may run up to N cycles; you are at cycle k") — the drive already knows both; expose them in the hail preamble (isaac-hail renders it). Remove "the cycle budget comes from config" wording that invites guessing. Keep "HOLD + escalate", but escalate = a plan-band hail with `:reason :held` (which the loud path in (1) also catches).

## Scenarios (@wip, worker writes — isaac-hail features/delivery.feature family; existing steps: bound delivery, ticks, turn ends, log matching, sole delivery EDN, hail records; bean-state fixture step needed)
1. a work turn that ends with the bean still in-progress and no handoff is re-delivered to the same session with continuations 1 (`:hail/work-held`)
2. a work turn that ends with the bean tagged unverified is delivered (no continuation)
3. a work turn that sent a plan-band hail during the turn is delivered (escalated, not held)
4. the fifth continuation that still ends held is abandoned with attention (`:hail/work-abandoned`, one post)
5. the hail preamble for a work band states the configured cycle limit (isaac-agent renders `:cycle-limit` into the turn; assert the system preamble contains "up to 250 cycles")

## Acceptance
```
cd isaac-hail && bb features features/delivery.feature && bb ci
cd isaac-agent && bb features features/bridge && bb ci   # preamble
```
Field: a bean that a worker holds is re-delivered within a minute; `grep work-held server.log` shows it; no planner hand re-hails for a week.
