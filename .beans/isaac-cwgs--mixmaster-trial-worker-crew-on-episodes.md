---
# isaac-cwgs
title: 'Mixmaster: trial worker crew on episodes'
status: in-progress
type: task
priority: normal
created_at: 2026-09-29T23:32:13Z
updated_at: 2026-09-30T14:03:18Z
blocked_by:
    - isaac-jvwr
    - isaac-3ljt
---

Ops on **zanebot**. Design: Micah + planner, 2026-09-29.

## Why

Try episodic memory on a worker before touching Scrapper. Micah's bet:
recall of similar past module work helps a worker build the context it
needs, while `:context-mode :reset` keeps old turns out of the request.

## Plan

- New crew **mixmaster** (Constructicon) cloned from `crew/scrapper.edn` +
  `scrapper.md` (soul renamed), plus `:session-policy :episodes` and
  `:recall/*` in `:tools :allow`. Keep `:context-mode :reset` and `:cycle`.
- Recall half-life shorter than the 30-day default (start ~7 days) if it
  can be set per crew; otherwise note the global value and leave it.
- Fresh session **isaac-work-4** on mixmaster with the worker tags; never
  `sessions set <id>.crew` on an existing session.
- Route a handful of beans in modules other workers have already touched
  to isaac-work-4.

## Measure

- Tokens per bean vs Scrapper on comparable beans.
- Was the first-turn recall on target? (read the injected block)
- Seal cost (gist + embedding calls) per bean.
- Recall tool calls in the first turn (fewer = injected recall is enough).

## Todo

- [x] Crew + soul + session created; `config validate` clean (2026-09-29: crew/mixmaster.edn + mixmaster.md on zanebot, session isaac-work-4 with no tags so the isaac-work pool never picks it; hail it with --band isaac-work --session isaac-work-4. Recall half-life is global only ([:recall :half-life]), left at 30d.)
- [ ] First bean hailed to isaac-work-4; recall seen in the request
- [ ] Findings written back here after ~5 beans

## 2026-09-30 deploy

Episodes 8ef1955 live (recall rides the prompt, seal skips recall, :episodes crew overrides, append-checkpoint!); agent a6231af (parallel queue). Mixmaster crew now has :episodes {:recall {:half-life 7}}. His recall reaches the model from here on; jvwr landed via a Scrapper, 0lb7 was his first bean (no recall, crashed at checkpoint after landing).

## Trial data so far (2026-09-30, planner)

No Mixmaster turn has had recall delivered yet, so these are baselines, not a comparison. On 3ljt the episodes policy logged 10 recalled lineage scenes, but the block was stripped before it reached the model (isaac-klcb: held on the agent session record, which the sidecar store conforms away).

| Turn | Crew | Bean | Requests | Prompt tokens | Output | Tools | Notes |
|---|---|---|---|---|---|---|---|
| f4cc2421 | Mixmaster | jvwr (attempt 1) | 26 | 1.51M | 6.4k | 31 | stopped on a contract conflict |
| b0ae2ae6 | Scrapper | jvwr (landed) | 33 | 1.34M | 4.5k | 43 | |
| 2de84201 | Mixmaster | 0lb7 | ~50 | n/a | n/a | n/a | landed, then crashed at checkpoint (rmbz) |
| e3cd7bbf | Mixmaster | 3ljt | 55 | 3.09M | 12.0k | 61 | recall computed, not delivered (klcb) |

Mixmaster made **zero** recall__search / recall__scene calls across all four turns. The real comparison starts after klcb deploys.

## Recall live (2026-09-30 14:02Z)

Episodes aa839f3 deployed (klcb + 3ljt). Smoke: a cold Pilot session stored its opening message with the recall block ahead of the prompt, and the model called recall__scene on an injected id. Mixmaster's next bean is the first real trial data point.
