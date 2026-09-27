---
# isaac-ey7a
title: Resource pool types, configuration, and lease receipts
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
---

Likely repo: **isaac-agent**. Design: Micah + planner, 2026-09-27. This is the generic contract; it does not implement a database driver.

## Contract to plan

- Declare `:isaac.agent/resource-pool-types` in Agent. Modules contribute a type factory and type-specific config schema; registration does not create a lease.
- Configure named instances under `:resource-pools` with a `:type`; turn frequencies refer to pool names. Unknown types, unknown names, and invalid type-specific config fail validation.
- A successful acquisition returns a **lease receipt**: typed bindings plus an opaque release identity. Some receipts have no binding.
- **Turn leases only.** A lease is acquired at admission and released when the turn ends: success, error, cancellation, or death. Tool-call-scoped leases are deferred to isaac-kxqj; the receipt shape leaves room for a scope, but only turn scope exists here.
- **Acquisition never blocks.** A pool type's `try-acquire` returns within a bounded time with either a receipt or `:busy`. It never waits on a lock, connection, or network. Admission serves every session; one blocking acquire would stall them all (same failure class as MCP on the turn path).
- **Agent coordinates.** If any requested pool is busy, Agent releases the leases it already took and the request stays waiting in the queue. This rollback lives here only; isaac-l3vb does not repeat it. Acquisition order must be deterministic and stated in scenarios.
- **Release is idempotent** by release identity: releasing twice (e.g. recovery replay) is harmless.
- **Release wakes waiters.** A release signals the existing queue release-token wake (isaac-ohsy) so waiting requests re-evaluate.
- **Restart reconciliation.** Agent persists release identities (never live handles) and on restart hands them back to their pool types to clean up leases held by dead turns.
- Bindings are applied at an Agent-defined seam before charge construction; pool implementations do not mutate arbitrary charges. Bindings are **per turn**, not session properties. The initial reserved binding is `:session/cwd`.

## Scenario plan to review

1. A contributed type validates two named pool instances and an unknown type fails loudly.
2. Two requested pools acquire; a busy second pool releases the first and leaves the request waiting.
3. A released lease wakes the waiting request, which then acquires both pools and runs.
4. A receipt supplies a binding before charge construction and releases on success, error, and cancellation.
5. After a restart, a lease left by a turn that died is reconciled and the member is usable again; a repeated release does not free a member held by another turn.

Draft until the scenarios are approved, committed `@wip`, and baselined. No new `:resources` config key or binding aliases are implied by this bean. The pool contract is exercised with a scripted pool type; Worksite's directory pool is isaac-npmp.
