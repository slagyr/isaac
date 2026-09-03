---
# isaac-o0bk
title: 'Scuttlebutt phase 1: isaac-discord mechanical migration to the new Comm protocol'
status: draft
type: task
tags:
    - scuttlebutt
    - discord
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-03T16:40:55Z
blocked_by:
    - isaac-jarr
---

Per isaac-frvu Discord contract, phase 1 rides the first pin bump past 5nxf. Mechanical: state-only deftype + (extend DiscordComm Comm (merge comm/defaults {...})) — never inline. on-reply posts the reply as a new message (replaces on-turn-end's content path); on-turn-end renders errors only; on-text-chunk/on-compaction-* disappear (compaction rows become on-bulletin defaults = silent); typing heartbeat (isaac-qomx) keeps running on on-turn-start/on-turn-end; everything else defaults. Zero UX change beyond phase 0. Bump the agent pin in deps.edn/bb.edn to the released 5nxf SHA. Phase 2 (working message) stays in isaac-pq0b. Draft until the @wip feature rows (comm-event tables: text-chunk→chatter, compaction→bulletin) are written.
