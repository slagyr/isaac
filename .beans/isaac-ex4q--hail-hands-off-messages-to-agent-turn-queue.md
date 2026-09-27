---
# isaac-ex4q
title: Hail hands off messages to Agent turn queue
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-27T22:45:05Z
blocked_by:
    - isaac-l3vb
    - isaac-70cr
---

Likely repos: **isaac-hail** and **isaac-agent**. Design: Micah + planner, 2026-09-27. This is the Hail half formerly bundled into isaac-l3vb.

## Contract to plan

- Hail remains the out-of-band message surface: send tool, HTTP/CLI ingress, band naming and prompt/data expansion, `reply_to` threading, message IDs, and message receipts.
- Once a message resolves to a turn request, submit it to Agent using the hail ID as an idempotency/source key. Record the Agent request ID only after Agent durably accepts it. Hail's receipt means accepted for delivery, not that the turn completed.
- Agent owns session candidate selection, capacity waiting, pool leases, charge construction, turn starts, and recovery. Remove Hail's independent delivery polling/binding/retry policy for these conditions. Hail must not call the drive directly.
- Preserve direct-session and band addressing plus undeliverable-message diagnosis. On restart at either side of the handoff, one hail produces at most one Agent request.

## Scenario plan to review

1. A band hail expands its prompt and submits one Agent request preserving hail/thread identity.
2. Busy session or pool remains in Agent's queue; Hail has no second capacity wait.
3. A crash/retry at the handoff does not create duplicate requests or turns.
4. A direct-session hail and an undeliverable address retain their expected message outcomes.

Draft until scenarios are committed and baselined. Foreman's ordinary `:turn` action uses Agent directly, without a Foreman-to-Hail dependency.
