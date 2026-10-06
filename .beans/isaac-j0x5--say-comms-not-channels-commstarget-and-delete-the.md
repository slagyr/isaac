---
# isaac-j0x5
title: 'Say comms, not channels: :comms/:target, and delete the dead session channel fields'
status: todo
type: task
priority: high
created_at: 2026-10-06T21:47:23Z
updated_at: 2026-10-06T21:47:23Z
---

Micah, 2026-10-06. Isaac says **comms** and **targets**, never "channel" (ISAAC.md: "comm" was chosen so "channel" stays Discord's word). isaac-mve9/rjeg/9khs shipped with `:channels` on sessions and `:channel` on send results; and the session record still carries three dead "channel" fields. Design: isaac/doc/design-conversations-and-comms.md.

Audit (every isaac-* repo, origin/main): `:last-channel` / `:last-to` are written on every append that carries `:channel`/`:to` and read by nothing but their own specs and `features/session/keys.feature`. Session `:channel` (the comm name, set at creation by discord/gchat/imessage) is read by nothing; it duplicates `:origin {:kind …}`.

## Shape
1. Rename the session field `:channels` → `:comms` (set of `"<comm>:<target>"`), written by each comm on inbound (gchat, discord, imessage).
2. Rename the comm send result `:channel` → `:target` (the resolved target the comm posted to); the delivery worker reads `:target`.
3. Delete `:last-channel`, `:last-to` and `:channel` from the session schema and both stores (sidecar/memory); comms stop passing `:channel`/`:to` on messages and `:channel` at session creation. `features/session/keys.feature` is already retired (agent b02706c).
4. Sessions on disk that carry the old keys are hand-cleaned at deploy (no legacy awareness in code).

## Acceptance (gated)
- The @wip scenarios pass with @wip removed: isaac-agent `features/comm/delivery/comm_continuity.feature` (4), isaac-gchat `features/comm/gchat/outbound.feature` (the DM continuity scenario), isaac-discord `features/comm/discord/comm_continuity.feature` (2), isaac-imessage `features/comm/imessage/comm_continuity.feature` (1).
- `the following sessions exist:` takes `comms` (not `channels`).
- One-time: no `:channels`, `:last-channel`, `:last-to`, or session/message `:channel` key remains in src/spec of isaac-agent, isaac-gchat, isaac-discord, isaac-imessage (Discord's own channel ids/URLs and local variable names are out of scope); the memory/sidecar/store specs for last-channel/last-to are deleted.
- Handbook (agent sessions/delivery, gchat, discord, imessage) says `:comms` / `:target`.
- gchat, discord, imessage pin the agent sha carrying this; pins coherent.
- `bb ci` green in all four; `bb jvm-spec` green in isaac-agent.
- Deploy note (manual): strip `:channels`, `:channel`, `:last-channel`, `:last-to` from session.edn on zanebot and yopp.

Likely repo scope: isaac-agent, isaac-gchat, isaac-discord, isaac-imessage.

feature-baseline: isaac-agent cbdc74cabf33525268a6e77f59c74590672dac2b
feature-baseline: isaac-gchat 278ff0105c27162026f98db3eafb121353f9110d
feature-baseline: isaac-discord 50de66e03a917ca366ba2a2bd1b7e10d3481077c
feature-baseline: isaac-imessage fcc3c3aa79398a85eb2478388af1eae3d9bb4d2a
feature-blob: isaac-agent features/comm/delivery/comm_continuity.feature a9b899e7eea48fd725813e7617a6f86e95e825d5
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 84834e4a2ff101fcc95ce9193f0d4e0cc3d8c90b
feature-blob: isaac-discord features/comm/discord/comm_continuity.feature 81c284226ac744b86744b2f39d77cdbc268169ae
feature-blob: isaac-imessage features/comm/imessage/comm_continuity.feature d820fda9b1b1b891a9331630dbc1aeda463d7875
