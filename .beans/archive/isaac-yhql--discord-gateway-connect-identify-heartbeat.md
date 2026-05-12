---
# isaac-yhql
title: "Discord: Gateway connect + identify + heartbeat"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T23:07:09Z
updated_at: 2026-04-21T01:03:36Z
---

## Description

Milestone 1 of the Discord channel adapter epic. Establish and maintain a Discord Gateway WSS connection: connect, send IDENTIFY with the bot token, receive HEARTBEAT_INTERVAL, start heartbeating, stay connected.

Scope:
- WSS client against the Discord Gateway URL (wss://gateway.discord.gg/?v=10&encoding=json)
- Receive HELLO op, extract heartbeat_interval
- Send IDENTIFY with token + intents (GUILD_MESSAGES + MESSAGE_CONTENT minimum)
- Heartbeat loop on schedule
- Receive READY op — bot shows as online

Out of scope for this bead: MESSAGE_CREATE handling (bead 2), reconnect (bead 7). A disconnect in this bead simply logs + exits the connection loop.

Implementation notes:
- Use http-kit's websocket client (bb-compatible)
- Protocol shape lives under src/isaac/channel/discord/gateway.clj
- Token from :channels :discord :token in config

## Acceptance Criteria

1. Implement src/isaac/channel/discord/gateway with connect, IDENTIFY, heartbeat loop, READY handling. State machine observable to tests.
2. Add the 7 step-defs listed above (or a suitable subset/variant — the scenarios are the contract).
3. Remove @wip from all 3 scenarios in features/channel/discord/gateway.feature.
4. bb features features/channel/discord/gateway.feature passes (3 examples).
5. bb features passes overall.
6. bb spec passes (unit specs covering malformed-frame and error edge cases land in the same bead or a sibling).

## Design

Implementation notes:
- Hand-rolled on http-kit (already a dep; bb + JVM compatible). Reference discljord and JDA for protocol structure, not runtime.
- Namespace: src/isaac/channel/discord/gateway.clj. Knows nothing about Isaac sessions or crews — just 'WSS event pump + REST send.' The isaac.channel protocol impl lives in a sibling ns (later milestones).
- State machine: disconnected → hello-received → identified → ready. Outgoing frames: IDENTIFY (op 2), HEARTBEAT (op 1). Incoming frames: HELLO (op 10), READY (dispatch), HEARTBEAT_ACK (op 11).
- IDENTIFY payload: {token, intents, properties: {os, browser, device}}. Intents = 33280 for v1 (GUILD_MESSAGES 512 | MESSAGE_CONTENT 32768).
- Heartbeat: schedule on a clock reference (dynamic var or passed-in fn). Production uses System/currentTimeMillis; tests inject a virtual clock that advances on demand. First heartbeat sends d: null (no sequence yet); subsequent heartbeats send the last received sequence number.
- Connect URL: wss://gateway.discord.gg/?v=10&encoding=json.
- Out of scope: reconnect on disconnect (bead isaac-qhf6), MESSAGE_CREATE receive (bead isaac-yexn), REST send (bead isaac-lkiy). A disconnect in this bead logs and exits.

Step-defs to add (under spec/isaac/features/steps/discord.clj or similar):
1. 'the Discord Gateway is faked in-memory' — stands up an in-memory fake Gateway that records outgoing frames and pushes inbound events on demand.
2. 'Discord is configured with:' — table of config keys (token, intents, ...) written into gherclj state.
3. 'the Discord client connects' — starts the gateway client pointing at the fake.
4. 'the Gateway sends HELLO:' / 'sends READY:' — table of payload fields; the fake dispatches the op to the client.
5. 'the test clock advances {int} milliseconds' — advances the virtual clock, firing any timers scheduled within that window.
6. 'the Discord client sends IDENTIFY:' / 'sends HEARTBEAT' — asserts the named op was sent; table form asserts payload field equality.
7. 'the Discord client is connected' — asserts post-READY state.

## Notes

Implemented Discord gateway state machine with HELLO -> IDENTIFY -> HEARTBEAT -> READY flow, added in-memory fake gateway feature steps, and covered malformed-frame/disconnect edge cases in unit specs. Verification: bb features features/channel/discord/gateway.feature, bb features, bb spec, bb spec spec/isaac/channel/discord/gateway_spec.clj.

