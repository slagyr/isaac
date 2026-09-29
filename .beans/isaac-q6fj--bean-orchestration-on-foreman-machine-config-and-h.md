---
# isaac-q6fj
title: 'Bean orchestration on Foreman: the bean-work machine (happy path and failure rows)'
status: draft
type: feature
priority: normal
created_at: 2026-09-27T23:09:07Z
updated_at: 2026-09-29T13:59:46Z
parent: isaac-q3u3
blocked_by:
    - isaac-npmp
    - isaac-lr8h
    - isaac-1qgv
---

Likely repos: **orchestration** (machine config, crew prompts/skills, `bb test-machines`) and **isaac** (bean workflow references). Merged 2026-09-29 (Micah + planner): isaac-1rtr (failure paths) folded in.

## Ungated

The orchestration repo has no feature runner, so this bean carries no Gherkin baseline. Its acceptance is machine tests run by `isaac foreman test` (isaac-1qgv) plus live runs on zanebot. It adds **no code to Isaac** — config, prompts, skills, and docs only; no legacy behavior.

## Contract

- A **bean-work Foreman machine** (`config/machines/bean-work.edn` in orchestration): planned/todo → claimed work → gated completion or ungated verification → return for repair → held / human escalation → completed. Preserve the gated-vs-ungated bean rule (isaac/AGENTS.md).
- Work turns are `:turn` actions against a logical session pattern (`:frequencies`) and the Worksite pool (`:resource-pools`); Foreman never names a session or directory.
- **Failure rows** (from isaac-1rtr): verification failure returns to work; all worksite members busy → the turn waits (Agent), no machine change; restart mid-handoff resumes without a duplicate turn (idempotency keys); a turn that ends without its crew signal takes a `:turn-ended` backstop row (nudge / re-dispatch / escalate); a provider-auth outage parks the turn (Agent weather) and the bean shows as waiting, not failed; human escalation → `held`, visible in `isaac foreman list --state held`.
- **Crew side:** worker and verifier prompts/skills call `foreman__signal` (e.g. `:landed`, `:verified`, `:repair`, `:blocked`) instead of hailing the next role. Planner dispatch = `isaac foreman start bean-work <bean-id>`.
- **Runs alongside the hail-dispatch path** during rollout: only beans explicitly started on the machine use it. The overlap is config-only and ends in isaac-20gd.

## Acceptance

- [ ] `bb test-machines` in orchestration passes: cases for the happy path (gated and ungated), repair loop, missing signal backstop, escalation to held, and an unexpected event.
- [ ] Live on zanebot: one gated bean goes todo → completed through Foreman with a Worksite pool; one ungated bean through verification; `isaac foreman status` / `history` name the current step and owner at each point.
- [ ] Live on zanebot: a deliberately failed verification returns the bean to work and it then completes.
- [ ] No Isaac source changes; config validates (`isaac config validate`) on zanebot.

Blocked by isaac-1qgv (machine tests) and isaac-npmp/isaac-lr8h (landed).
