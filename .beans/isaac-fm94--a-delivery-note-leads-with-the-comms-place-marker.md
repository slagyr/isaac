---
# isaac-fm94
title: A delivery note leads with the comm's place marker (gchat thread)
status: todo
type: bug
priority: high
created_at: 2026-10-06T23:09:43Z
updated_at: 2026-10-06T23:09:43Z
---

Micah, 2026-10-06, from the live continuity test on yopp. The delivery note landed in the DM session (isaac-mve9/j0x5 work), but with no thread: a gchat post with no `gchat/thread` starts a new thread, and inbound lines carry a `[thread:xxxxxxxx]` marker (canon/thread-marker) that the note lacked. Yopp could not tie the note to the thread Micah answered in, the same misread risk the work was meant to remove.

## Shape
- A comm's send result may report `:marker`: the tag an inbound line from the same place carries. gchat: `canon/thread-marker` of the thread posted into, the request's `gchat/thread` or, for a new thread, the thread named in Chat's create response.
- The delivery worker leads the note with it: `<marker> [sent here by crew <crew> from session <session>] <content>`. No marker means the plain note (Discord, iMessage).
- Fixture: the gchat fake answers `messages.create` with `:thread {:name …}`: the request's thread, or `<space>/threads/posted` for a new one.

## Acceptance (gated)
- The @wip scenario in isaac-agent `features/comm/delivery/comm_continuity.feature` and the 2 @wip scenarios at the end of isaac-gchat `features/comm/gchat/outbound.feature` pass with @wip removed.
- isaac-gchat pins the agent sha carrying this; pins coherent.
- `bb ci` green in both; `bb jvm-spec` green in isaac-agent.
- Handbook (gchat outbound, agent delivery) mentions the marker.

Likely repo scope: isaac-agent, isaac-gchat. Deploy: zanebot (agent) and yopp (agent + gchat).

feature-baseline: isaac-agent a2e4dde57e45d0a35acb03558aa33dedcbe48d2e
feature-baseline: isaac-gchat c2928c0c12ac8c7cd6b112488cfc2218f5bbc4d6
feature-blob: isaac-agent features/comm/delivery/comm_continuity.feature 23cd72467b275b57ef8b1fafcb58b3d6bd55e516
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 24ec05af92876beea51770a69036e920b4bc490f
