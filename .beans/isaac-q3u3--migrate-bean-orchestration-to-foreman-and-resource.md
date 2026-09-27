---
# isaac-q3u3
title: Migrate bean orchestration to Foreman and resource pools
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-27T22:45:05Z
blocked_by:
    - isaac-npmp
    - isaac-lr8h
    - isaac-ex4q
---

Likely repos: **orchestration** (deployed config/prompts) and **isaac** (bean workflow references). Design: Micah + planner, 2026-09-27. End-to-end migration after the Agent, Worksite, Foreman, and Hail seams land.

## Contract to plan

- Configure a machine for the bean lifecycle: planned/todo, claimed work, verification or gated completion, return for repair, held/human escalation, terminal completion. Preserve the project's gated-vs-ungated bean rule; settle exact rows during scenario planning.
- Foreman submits ordinary work turns to Agent against a logical session selector and Worksite pool. Crew signals and turn observations advance the machine; status/history answer which step owns the bean.
- Replace worker-to-worker Hail handoff prompts in this workflow with Foreman events/actions. Keep Hail available for independent messages or explicit human attention where its messaging semantics are useful.
- Exercise a fresh bean through the happy path, verification failure/repair, resource exhaustion, restart during a handoff, and a turn ending without a signal. A duplicated event or turn request must not duplicate work.
- After the new path runs successfully, remove obsolete orchestration band choreography. Hail and Worksite repository retirement are separate decisions based on their remaining uses.

## Scenario plan to review

1. Gated bean reaches completion through Foreman with a Worksite pool.
2. Ungated bean routes through verifier and returns to work on failure.
3. Busy worksite waits; release starts exactly one pending turn.
4. Restart and missing crew signal produce inspectable, recoverable machine states.
5. Human escalation and operator status/history identify the current owner.

Draft until its executable scenarios and target deployment checks are approved. No live dispatch is authorized by this planning bean.
