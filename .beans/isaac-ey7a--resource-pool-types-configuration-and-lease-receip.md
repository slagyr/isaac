---
# isaac-ey7a
title: Resource pool types, configuration, and lease receipts
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T22:33:11Z
---

Likely repo: **isaac-agent**. Design: Micah + planner, 2026-09-27. This is the generic contract; it does not implement a database driver.

## Contract to plan

- Declare `:isaac.agent/resource-pool-types` in Agent. Modules contribute a type factory and type-specific config schema; registration does not create a lease.
- Configure named instances under `:resource-pools` with a `:type`; turn frequencies refer to pool names. Unknown types, unknown names, and invalid type-specific config fail validation.
- A successful acquisition returns a **lease receipt**: typed bindings plus an opaque release identity. Some receipts have no binding. Agent coordinates acquisitions and releases earlier leases if a later pool cannot be acquired.
- Support turn-scoped and tool-call-scoped leases without storing live handles in a charge, transcript, or durable request. A concrete database adapter is deferred; use a scripted pool type to prove tool-call lifetime.
- Bindings are applied at an Agent-defined seam before charge construction; pool implementations do not mutate arbitrary charges. The initial reserved binding is `:session/cwd`.

## Scenario plan to review

1. A contributed type validates two named pool instances and an unknown type fails loudly.
2. Two requested pools acquire; a busy second pool releases the first and leaves the request waiting.
3. A receipt supplies a binding before charge construction and releases on success, error, and cancellation.
4. A tool-call lease is acquired only when the tool runs and is released after that call.

Draft until the scenarios are approved, committed `@wip`, and baselined. No new `:resources` config key or binding aliases are implied by this bean.
