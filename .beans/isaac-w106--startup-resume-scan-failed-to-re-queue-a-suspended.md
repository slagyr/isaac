---
# isaac-w106
title: Startup resume scan failed to re-queue a suspended delivery in production
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-07-07T18:28:44Z
updated_at: 2026-07-07T20:18:05Z
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

## Implementation Notes

- Confirmed `isaac-server` already had the startup resume wiring on `main` (`04f6b9b`), so the open acceptance gap was not missing boot invocation but missing boot-summary visibility from the resume scan.
- In clean sibling checkout `isaac-agent-w106` (branched from pinned `fb46da6` as `bean/isaac-w106`), updated `src/isaac/bridge/resume.clj` so `resume-interrupted-turns!` emits `:resume/scan-complete` with structured counts: `:markers`, `:requeued`, and `:dropped`.
- Added focused unit coverage in `isaac-agent-w106/spec/isaac/bridge/resume_spec.clj` covering:
  - zero-marker boot scan logs `:resume/scan-complete`
  - suspended hail marker increments `:requeued`
  - stale comm marker increments `:dropped`
- Verified the existing agent feature still passes: `bb features features/session/resume_repair.feature`.
- Also finished the small `isaac-server` cleanup noted in the prior memory: `src/isaac/server/app.clj` now uses local `resolve-var` consistently for startup resume lookup, and `spec/isaac/server/app_spec.clj` proves the resume boot phase runs before `worker/start!`.
- Commits:
  - `isaac-agent` branch `bean/isaac-w106`: `94db1f9` — `Log startup resume scan summary`
  - `isaac-server` `main`: `fb6dcd5` — `Use resolve-var for startup resume wiring`
- Verification run:
  - `isaac-agent-w106`: `clojure -M:spec spec/isaac/bridge/resume_spec.clj`
  - `isaac-agent-w106`: `bb features features/session/resume_repair.feature`
  - `isaac-agent-w106`: `bb verify`
  - `isaac-server`: `bb spec spec/isaac/server/app_spec.clj`
  - `isaac-server`: `bb spec`
  - `isaac-server`: `bb features`
- Suite results:
  - `isaac-agent-w106` verify: `1176 examples, 0 failures, 2316 assertions` and `590 examples, 0 failures, 1329 assertions`
  - `isaac-server` specs/features: `171 examples, 0 failures, 319 assertions` and `55 examples, 0 failures, 135 assertions`
