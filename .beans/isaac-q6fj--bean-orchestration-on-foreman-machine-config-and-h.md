---
# isaac-q6fj
title: 'Bean orchestration on Foreman: machine config and happy path alongside hail'
status: draft
type: feature
created_at: 2026-09-27T23:09:07Z
updated_at: 2026-09-27T23:09:07Z
parent: isaac-q3u3
blocked_by:
    - isaac-npmp
    - isaac-lr8h
---

Likely repos: **orchestration** (deployed machine config, prompts) and **isaac** (bean workflow references). First of three migration beans under milestone isaac-q3u3.

## Contract to plan

- Configure a bean-lifecycle machine: planned/todo, claimed work, gated completion or verification, return for repair, held/human escalation, terminal completion. Preserve the gated-vs-ungated bean rule; settle exact rows during scenario planning.
- Foreman submits work turns to Agent against a logical session pattern and a Worksite pool. Crew signals and turn observations advance the machine; `isaac foreman status/history` answers which step owns the bean.
- **Runs alongside today's hail path.** Only beans explicitly started on the machine use it; band choreography stays untouched for everything else.

## Scenario plan to review

1. A gated bean reaches completion through Foreman with a Worksite pool.
2. An ungated bean routes through verification and completes.
3. Status and history name the current step and owner at each transition.

Draft until scenarios and the target deployment checks are approved. No live dispatch is authorized by this planning bean.
