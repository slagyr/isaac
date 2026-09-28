---
# isaac-ex4q
title: Hail hands off messages to Agent turn queue
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-28T01:32:07Z
parent: isaac-q3u3
blocked_by:
    - isaac-l3vb
    - isaac-70cr
    - isaac-5gu1
---

Likely repos: **isaac-hail** and **isaac-agent**. Design: Micah + planner, 2026-09-27. This is the Hail half formerly bundled into isaac-l3vb.

## Contract to plan

- Hail remains the out-of-band message surface: send tool, HTTP/CLI ingress, band naming and prompt/data expansion, `reply_to` threading, message IDs, and message receipts.
- Once a message resolves to a turn request, submit it to Agent using the hail ID as an idempotency/source key. Record the Agent request ID only after Agent durably accepts it. Hail's receipt means accepted for delivery, not that the turn completed.
- Agent owns session candidate selection, capacity waiting, pool leases, charge construction, turn starts, and recovery. Remove Hail's independent delivery polling/binding/retry policy for these conditions. Hail must not call the drive directly.
- **"Hails never die" moves to Agent.** Infrastructure-failure deferral with attention and auto-resume on recovery is now Agent's (isaac-70cr). Hail drops its own deferral/attention path for turn failures; it may relay Agent's attention observation to a human or to `reply_to`, but it never re-submits or retries. Dead-letter stays for poison messages only.
- Preserve direct-session and band addressing plus undeliverable-message diagnosis. On restart at either side of the handoff, one hail produces at most one Agent request.

## Scenario plan to review

1. A band hail expands its prompt and submits one Agent request preserving hail/thread identity.
2. Busy session or pool remains in Agent's queue; Hail has no second capacity wait.
3. A crash/retry at the handoff does not create duplicate requests or turns.
4. A provider auth failure parks the request in Agent with attention; Hail neither retries nor dead-letters it, and the turn runs on recovery.
5. A direct-session hail and an undeliverable address retain their expected message outcomes.

Draft until scenarios are committed and baselined. Foreman's ordinary `:turn` action uses Agent directly, without a Foreman-to-Hail dependency.


## Decisions (2026-09-27, Micah + planner)

1. **Fan-out is killed** (Micah): no `:reach :all`, and the `:reach` key is removed everywhere in isaac-5gu1, which lands first. This bean drops Hail's broadcast path with the rest of delivery.
2. **Hail tracks outcomes with its own turn observer** (Micah: "makes a lot of sense"). Lifecycle in `hail_get`: pending → submitted (Agent request id) → delivered | failed (from the observer) | undeliverable (Agent refused).
3. **Submit = queue.** At send time, after the hail record is persisted, Hail expands the band and submits the turn request to Agent's queue keyed by hail id. Agent queues it; it never runs inline, so a crew's `hail-send` never runs another session's turn inside its own tool call. A server-start sweep submits any persisted hail without a request id.
4. **Retries belong to the drive** (Micah). Hail never retries a turn. Dead-letter stays only for poison (unparseable/invalid) hails.
5. **Deploy:** drain in-flight hails on zanebot before cutover; `hail/deliveries` goes away.
6. **One bean** — splitting would leave two delivery paths.

## Scenario survey (2026-09-27)

Of 160 Hail scenarios: 71 keep, 20 rewrite ("the hail delivery worker ticks" → "the turn queue ticks at …"; the turn still runs, so assertions mostly stand), 69 removed as Agent-owned delivery (session selection, bind, busy deferral, retry/dead-letter, weather, marker claim/resume, context-window guard, slash commands) — Agent has its own scenarios for these. `delivery_worker.clj` and the selection half of `router.clj` are deleted.

- Remove: bound_unclaimed (all 7), commands (1), context_window_guard (3), dead_letter_resurrection (3), delivery (23 of 25; keep+rewrite :354, :384), explicit-session-routing (4), hail-band-prompts :114, router (15; keep :238, :273, :424), session-create (6), turn-marker-claim (3), turn-resume (3).
- Rewrite: band-inheritance :21; delivery :354, :384; hail-band-data :11, :49, :101; hail-band-prompts :30; hail-get :91, :114; hail-metadata (all 6); hail-naming :40; hail-threading :31, :71; http :58, :161.
