---
# isaac-yleu
title: 'Scuttlebutt phase 1: isaac-imessage mechanical migration to the new Comm protocol'
status: draft
type: task
tags:
    - scuttlebutt
    - imessage
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-03T16:40:56Z
blocked_by:
    - isaac-jarr
---

Per the iMessage review (isaac-frvu, decided 2026-08-31): pure delivery. Replace the eight no-op turn methods with comm/defaults via extend; send! is the entire implementation; replies keep riding the outbound delivery queue (result->reply-text → chunker moves under on-reply only if the delivery path needs it — otherwise defaults). Break-glass alerts remain the attention system's job (isaac-vrtb), never the comm. Bump agent pin to the released 5nxf SHA; module bb features + bb spec green.
