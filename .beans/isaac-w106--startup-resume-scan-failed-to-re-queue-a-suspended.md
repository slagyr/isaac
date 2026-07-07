---
# isaac-w106
title: Startup resume scan failed to re-queue a suspended delivery in production
status: todo
type: bug
priority: critical
created_at: 2026-07-07T18:28:44Z
updated_at: 2026-07-07T18:28:44Z
---


## Gap

First production suspend→resume cycle with a genuinely in-flight turn, and the resume half failed silently. Evidence (zanebot, 2026-07-07):

- 16:37:09 — deploy restart (acp 0.1.7 train). Suspend phase worked: perceptor's in-flight LLM stream aborted (`:chat/stream-error :error :cancelled`), `:hail/delivery-suspended` logged for delivery c27493a3 (isaac-axzg verification, session isaac-verify).
- 16:37:28+ — boot ran (activate/start phases logged). **Zero `:resume/` events. No re-queue. No delivery back in deliveries/.**
- 100+ minutes later the turn marker still sat at sessions/turns/isaac-verify.edn with the embedded delivery — visible head of the preserved marker (/tmp/axzg-stale-marker-evidence.edn on zanebot) shows session-id/delivery-id/started-at/attempts/delivery but NO obvious :suspended stamp, which may itself be a second finding: suspend logged the delivery-suspension but may not have stamped the marker.
- Board effect: isaac-axzg frozen in-progress with no signal — the exact stranding isaac-vdfc exists to prevent.

## Questions for the worker

1. Does the resume scan actually run at server startup (the vdfc acceptance item "wired BEFORE the delivery worker's first tick")? No :resume/* log lines appear at boot even when a marker exists — if the scan runs but processes 0-or-N markers silently, add a boot summary log (`:resume/scan-complete markers=N requeued=N dropped=N`); if it doesn't run, that's the bug.
2. Did suspend stamp the marker (:suspended/:boundary)? If not, the F2 stamp path has a gap too — but note resume must re-queue BOTH stamped (attempts unchanged) and unstamped (attempts+1) markers, so a missing stamp alone cannot explain a no-op.

## Acceptance (spec to confirm with scenarios against the real boot path)

The existing vdfc scenarios pass at the resume-fn level; this bug is about the SERVER BOOT WIRING — the scenario must exercise the actual startup sequence (isaac-server features), not a direct resume invocation: Given a suspended/orphaned marker on disk, When the server boots, Then the delivery is re-queued and a `:resume/scan-complete` summary is logged.

## Recovery applied meanwhile

Fresh verify hail d4e5730a sent for isaac-axzg (2026-07-07 ~18:2xZ); stale marker preserved as evidence.
