---
# isaac-kfjf
title: "Delivery queue: reliable outbound comm retries"
status: completed
type: feature
priority: normal
created_at: 2026-04-22T16:13:23Z
updated_at: 2026-04-22T17:19:21Z
---

## Description

Persistent retry infrastructure for outbound comm posts. Without this, every channel adapter reinvents retry or simply drops on transient failure (which is Isaac's current Discord REST behavior).

Scope (v1):
- Per-message EDN files under <state-dir>/delivery/pending/<id>.edn and <state-dir>/delivery/failed/<id>.edn
- Record shape: {:id, :comm, :target, :content, :attempts, :next-attempt-at, :created-at}
- Worker loop inside the Isaac server process polls pending/, attempts due deliveries, handles outcomes
- Fixed retry policy: 5 attempts with exponential backoff at 1s, 5s, 30s, 2m, 10m (total ~13 min)
- On success: delete the pending/ file
- On transient failure: increment attempts, set next-attempt-at via backoff, keep in pending/
- On hitting max attempts: move to failed/ and emit :delivery/dead-lettered error log

Integration: comm adapters (Discord REST now, future iMessage/Slack/etc.) use a try-direct-then-enqueue pattern on their internal send path. Happy-path sends are immediate; failures enqueue for retry. No adapter-specific retry logic.

Out of scope (separate beads if needed):
- Configurable retry policy (per-message or per-comm)
- Retrying from failed/ via operator action (CLI)
- Retry-after header parsing (Discord rate-limit hints)
- Priority queueing
- Cross-comm routing policies

See features/delivery/queue.feature for the 3 @wip scenarios.

## Acceptance Criteria

1. Implement delivery queue storage, worker loop, backoff, and enqueue fallback integration in Discord REST.
2. Add the 3 step-defs listed above.
3. Remove @wip from all 3 scenarios in features/delivery/queue.feature.
4. bb features features/delivery/queue.feature passes.
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- Namespace: src/isaac/delivery/ with queue.clj, worker.clj, backoff.clj.
- Storage: id-named EDN files under <state-dir>/delivery/{pending,failed}/. id is a short uuid-v4 hex.
- Worker loop ticks periodically (default ~10s). For each pending file, skip if :next-attempt-at > now; otherwise attempt delivery.
- Delivery dispatch: look up comm adapter by :comm key; call its send fn with :target and :content. Result is success or failure.
- On success: (fs/delete! pending-file).
- On failure: increment :attempts, compute :next-attempt-at = now + backoff(attempts), write back to pending-file. If attempts == 5, move file to failed/ and log :delivery/dead-lettered.
- Backoff table: 1s, 5s, 30s, 2m, 10m — index by attempts (1→1s, 2→5s, ...).
- Integration: comm adapters' send fn expose (send! ctx target content). Wrap with (try-send-or-enqueue!) that catches failures and enqueues.

New step-defs to add:
1. 'the delivery worker ticks' — runs one worker pass at the current clock.
2. 'the delivery worker ticks at "<iso-instant>"' — binds clock + runs one pass.
3. 'the EDN state file "<relpath>" does not exist' — negative existence (pair with the existing contains:).

## Notes

Implemented delivery queue storage, backoff, worker loop, Discord enqueue fallback, and server worker startup in commit 64f00d9. Verified with bb features features/delivery/queue.feature, bb features, and bb spec.

