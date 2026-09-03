---
# isaac-yleu
title: 'Scuttlebutt phase 1: isaac-imessage mechanical migration to the new Comm protocol'
status: todo
type: task
priority: normal
tags:
    - scuttlebutt
    - imessage
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-03T17:47:18Z
blocked_by:
    - isaac-jarr
---

Per the iMessage review (isaac-frvu, decided 2026-08-31): pure delivery. Replace the eight no-op turn methods with comm/defaults via extend; send! is the entire implementation; replies keep riding the outbound delivery queue (result->reply-text → chunker moves under on-reply only if the delivery path needs it — otherwise defaults). Break-glass alerts remain the attention system's job (isaac-vrtb), never the comm. Bump agent pin to the released 5nxf SHA; module bb features + bb spec green.



## Planning (2026-09-03) — no new scenarios

Today `ImessageComm` no-ops all ten turn methods; replies ride the outbound delivery queue (reply.feature: turn result → delivery record → worker → AppleScript runner), never on-turn-end. Under the new protocol the deftype keeps its fields (`[host state*]`) and takes `comm/defaults` for every turn event; `send!` is the whole extend map. The full existing suite (intake, intake_filtering, lifecycle, reply, routing, send, turn_context, watch_hydrate) is the contract and must stay green against the new pin. Break-glass stays with attention (isaac-vrtb).

## Step ledger

| step | status |
|------|--------|
| all | reuse — no feature rows change in this bean |

## Acceptance

    cd isaac-imessage
    # deps.edn + bb.edn agent pin = the SHA isaac-jarr releases
    bb features
    bb spec
Shape: `(extend ImessageComm comm/Comm (merge comm/defaults {:send! ...}))` — never inline.
