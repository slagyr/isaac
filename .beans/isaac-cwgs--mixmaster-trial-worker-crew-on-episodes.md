---
# isaac-cwgs
title: 'Mixmaster: trial worker crew on episodes'
status: in-progress
type: task
priority: normal
created_at: 2026-09-29T23:32:13Z
updated_at: 2026-09-29T23:41:07Z
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
