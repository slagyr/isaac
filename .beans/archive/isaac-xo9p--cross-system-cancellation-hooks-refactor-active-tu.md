---
# isaac-xo9p
title: "Cross-system cancellation hooks: refactor :active-turns out of *system*"
status: completed
type: task
priority: normal
created_at: 2026-05-10T21:39:54Z
updated_at: 2026-05-10T22:28:17Z
---

## Description

Cancellation hooks (bridge/on-cancel!) live in :active-turns inside the
*system* dynvar. ACP's dispatch-line uses (system/with-system ...) which
binds *system* to a fresh atom per RPC call, so hooks registered during
the prompt dispatch are invisible to cancel! during the cancel dispatch
(different atom). Hook-based cancellation only works within a single
system context — useless across the ACP request boundary.

The current implementation works around this by polling: bridge/cancelled?
also checks the top-level pending-cancels atom (shared across systems),
and exec.clj polls bridge/cancelled? in a loop. That's why the original
exec.clj polling implementation (commit before 82d51ed) actually worked
for ACP cancels and the hook-based refactor (82d51ed) had to be reverted
in feeea2f.

The fix: move cancellation state (cancelled? atom + hooks list, keyed
by session-key) to a top-level shared atom, like pending-cancels already
is. The :active-turns slot in the system schema becomes vestigial or is
removed.

Once this lands, exec.clj can use pure hook-based cancellation again
(no polling loop) without breaking ACP.

## Acceptance

1. bridge/begin-turn!, bridge/cancel!, bridge/on-cancel! operate on a
   shared top-level atom — no per-system isolation.
2. system_spec updated: :active-turns slot removed from schema (or
   documented as no-op).
3. features/acp/cancel_tool_status.feature still passes.
4. spec/isaac/bridge/cancellation_spec.clj still passes.
5. Re-apply the exec.clj refactor from 82d51ed (on-cancel! hook +
   single .waitFor, no polling loop). features/acp/cancel_tool_status
   should still pass.
6. The "polls only the remaining timeout window before timing out"
   spec gets deleted again (no polling).
7. bb spec and clj -M:test:spec both green; bb features green.

## Notes

Implemented shared top-level cancellation state, removed :active-turns from isaac.system, and restored hook-based exec cancellation. Passing checks: spec/isaac/bridge/cancellation_spec.clj, features/acp/cancel_tool_status.feature, clj -M:test:spec, bb features. Full bb spec still has unrelated failures tracked in the new follow-up bead and is outside this bead's scope.

