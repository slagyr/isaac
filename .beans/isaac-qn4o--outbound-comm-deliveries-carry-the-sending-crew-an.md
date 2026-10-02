---
# isaac-qn4o
title: Outbound comm deliveries carry the sending crew and session, on the record and in the log
status: todo
type: feature
priority: normal
created_at: 2026-10-02T15:27:37Z
updated_at: 2026-10-02T15:27:44Z
---

Likely repo: **isaac-agent**. From the isaac-1hfe review, 2026-10-02 (Micah: "good hygiene, we want it anyway").

## Why

Every outbound comm message leaves through the delivery queue with no record
of who sent it. Apple, Discord and the rest see one bot handle, so "which
crew texted Micah, and from which session?" has no answer today. Stamping the
sender on the record and the log answers it for every comm, and gives the
server log a crew-aware wire trail with no new module.

## Design

- `comm_send` (`isaac.agent.tool.comm-send`) stamps `:crew` and `:session`
  on the delivery record it enqueues. Tools already receive `"session_key"`
  in their args; the crew is the session's crew (or the defaulted crew).
- `:comm.delivery/queued` and `:comm.delivery/delivered` (and the
  attempt-failed / deferred / dead-lettered lines via `audit-fields`) carry
  `:crew` and `:session` when the record has them.
- Records without them (older pending files, non-tool enqueues) still
  deliver; the fields are simply absent.
- Comms stay pipes: they receive the stamped record and may ignore the keys.

Out of scope: turn replies a comm sends itself (an inbound iMessage turn's
answer) do not go through the delivery queue. Listener/observer berths are
deferred (isaac-1hfe).

## Acceptance

- isaac-agent `features/tool/comm_send.feature` — "comm_send stamps the sending crew and session on the delivery (isaac-qn4o)"
- isaac-agent `features/comm/delivery/queue.feature` — "the delivered log names the crew and session that sent it (isaac-qn4o)"

feature-baseline: isaac-agent 4881de6e644f8e3a2829cb84b2e28f8cc65bee97
feature-blob: isaac-agent features/tool/comm_send.feature 01452e32378a4d6e0b0dc5f0e13ab0e29448e7a1 71
feature-blob: isaac-agent features/comm/delivery/queue.feature ef4bd393d2636a0fc1e2e08a31a22ecc6a7c763d 114
